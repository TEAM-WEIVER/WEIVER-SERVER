package com.weiver.interview.service;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.DetailAnalysisReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.repository.CultureReportRepository;
import com.weiver.analysis.repository.DetailAnalysisReportRepository;
import com.weiver.analysis.repository.TechnicalSkillReportRepository;
import com.weiver.analysis.service.CulturefitAxisService;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.global.event.util.EventIds;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.dto.request.InterviewAnswerSubmitRequest;
import com.weiver.interview.dto.request.InterviewStartRequest;
import com.weiver.interview.dto.response.InterviewAnalysisSubmitResponse;
import com.weiver.interview.dto.response.InterviewStartResponse;
import com.weiver.interview.dto.response.InterviewTurnDTO;
import com.weiver.interview.dto.response.InterviewWebSocketMessageResponse;
import com.weiver.interview.event.dto.InterviewQuestionGeneratedData;
import com.weiver.interview.event.dto.InterviewQuestionRequestedData;
import com.weiver.interview.event.dto.InterviewReportCompletedData;
import com.weiver.interview.event.dto.InterviewReportRequestedData;
import com.weiver.interview.event.dto.InterviewTranscriptSaveRequestedData;
import com.weiver.interview.event.dto.InterviewTranscriptSavedData;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.service.InterviewQuestionVoiceService.QuestionVoiceCommand;
import com.weiver.interview.type.InterviewSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class InterviewFlowService {

    private static final String SKILL_QUESTION_PREFIX = "S_";
    private static final String CULTURE_QUESTION_PREFIX = "C_";
    private static final String END_QUESTION_PREFIX = "E_";

    private final ApplicantRepository applicantRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final TechnicalSkillReportRepository technicalSkillReportRepository;
    private final DetailAnalysisReportRepository detailAnalysisReportRepository;
    private final CultureReportRepository cultureReportRepository;
    private final CulturefitAxisService culturefitAxisService;
    private final DomainEventPublisher domainEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final InterviewQuestionVoiceService interviewQuestionVoiceService;

    /**
     * 새 면접 세션을 만들고 첫 질문 생성 요청 이벤트를 발행한다.
     */
    public InterviewStartResponse startInterview(String applicantPublicId, InterviewStartRequest request) {
        Applicant applicant = applicantRepository.findByPublicId(applicantPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        // 면접 결과를 제출하면 1개월간 재응시할 수 없다.
        if (!applicant.isInterviewAvailableAt(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INTERVIEW_COOLDOWN_NOT_EXPIRED);
        }

        InterviewSession session = InterviewSession.builder()
                .applicant(applicant)
                .quarter(currentQuarter())
                .sessionStatus(InterviewSessionStatus.STARTED)
                .build();

        interviewSessionRepository.save(session);
        session.updateStatus(InterviewSessionStatus.WAITING_FOR_QUESTION);
        publishQuestionRequested(session, null, 1);

        return new InterviewStartResponse(
                session.getInterviewSessionId(),
                session.getSessionStatus().name()
        );
    }

    /**
     * 지원자 답변을 기존 질문 turn에 반영하고 다음 질문 생성 요청 이벤트를 발행한다.
     */
    public InterviewWebSocketMessageResponse submitAnswer(
            UUID interviewSessionId,
            String applicantPublicId,
            InterviewAnswerSubmitRequest request
    ) {
        InterviewSession session = getSessionForApplicant(interviewSessionId, applicantPublicId);
        if (isAnswerClosed(session.getSessionStatus())) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }

        boolean alreadyWaitingForQuestion = session.getSessionStatus() == InterviewSessionStatus.WAITING_FOR_QUESTION;
        InterviewTurnDTO answeredTurn = session.updateAnswer(
                request.questionCode(),
                request.sequence(),
                request.answer()
        );
        if (answeredTurn == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "답변 대상 질문을 찾을 수 없습니다.");
        }

        if (alreadyWaitingForQuestion) {
            return InterviewWebSocketMessageResponse.answerAccepted(session.getInterviewSessionId());
        }

        session.updateStatus(InterviewSessionStatus.WAITING_FOR_QUESTION);
        publishQuestionRequested(session, answeredTurn, answeredTurn.sequence() + 1);
        return InterviewWebSocketMessageResponse.answerAccepted(session.getInterviewSessionId());
    }

    /**
     * AI가 생성한 질문을 transcript에 멱등 append하고, 종료 질문이면 종료 처리한다.
     * 일반 질문은 커밋 후 TTS 작업을 전용 스레드풀에 넘기며, QUESTION_READY(텍스트 + 음성)는 그쪽에서 푸시한다.
     */
    public void handleQuestionGenerated(InterviewQuestionGeneratedData data) {
        long receivedAtNanos = System.nanoTime();
        validateQuestionGenerated(data);

        InterviewSession session = getSession(data.interviewSessionId());
        validateEventApplicant(session, data.applicantId());

        boolean appended = session.appendQuestion(
                data.nextQuestionCode(),
                data.sequence(),
                data.question()
        );
        if (!appended) {
            return;
        }

        if (isEndQuestion(data.nextQuestionCode())) {
            // 면접 Q&A 종료 표시까지만. 실제 제출(transcript 저장 요청)은 구직자가
            // requestAnalysis로 직접 요청할 때 시작한다. 종료 질문은 TTS 대상이 아니다.
            session.updateStatus(InterviewSessionStatus.FINISHED);
            sendInterviewMessage(
                    session,
                    InterviewWebSocketMessageResponse.interviewFinished(session.getInterviewSessionId())
            );
            return;
        }

        session.updateStatus(InterviewSessionStatus.QUESTION_READY);

        InterviewTurnDTO question = session.getTranscript().get(session.getTranscript().size() - 1);
        QuestionVoiceCommand command = new QuestionVoiceCommand(
                session.getInterviewSessionId(),
                session.getApplicant().getPublicId(),
                question.questionCode(),
                question.sequence(),
                question.question(),
                receivedAtNanos
        );
        // 커밋된 뒤에 TTS를 시작한다. 트랜잭션·리스너 스레드 안에서는 TTS를 부르지 않는다.
        runAfterCommit(() -> interviewQuestionVoiceService.dispatch(command));
    }

    /**
     * 구직자가 종료된 면접의 분석(최종 평가)을 직접 요청한다("면접 결과 제출하기").
     *
     * <p>AI 서버에 transcript 저장을 요청하고, 저장 완료 이벤트를 받으면 이어서 리포트 생성을 요청한다.
     * 분석을 요청한 시점이 "면접 결과 제출" 시점이므로, 이때부터 1개월간 재응시가 제한된다.
     *
     * @throws BusinessException 면접이 종료되지 않았거나({@link ErrorCode#INTERVIEW_NOT_FINISHED}),
     *                           이미 분석을 요청했거나 복구 불가 상태인 경우
     *                           ({@link ErrorCode#INTERVIEW_ANALYSIS_ALREADY_REQUESTED})
     */
    public InterviewAnalysisSubmitResponse requestAnalysis(UUID interviewSessionId, String applicantPublicId) {
        InterviewSession session = getSessionForApplicant(interviewSessionId, applicantPublicId);
        InterviewSessionStatus status = session.getSessionStatus();

        if (status != InterviewSessionStatus.FINISHED) {
            throw new BusinessException(isSubmitted(status)
                    ? ErrorCode.INTERVIEW_ANALYSIS_ALREADY_REQUESTED
                    : ErrorCode.INTERVIEW_NOT_FINISHED);
        }

        publishTranscriptSaveRequested(session);
        session.updateStatus(InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED);

        Applicant applicant = session.getApplicant();
        applicant.markInterviewSubmitted(LocalDateTime.now());

        return new InterviewAnalysisSubmitResponse(
                session.getInterviewSessionId(),
                session.getSessionStatus().name(),
                applicant.getNextAvailableScreeningAt()
        );
    }

    /**
     * AI 서버의 transcript 저장 완료를 반영하고 최종 리포트 생성 요청 이벤트를 발행한다.
     */
    public void handleTranscriptSaved(InterviewTranscriptSavedData data) {
        validateTranscriptSaved(data);

        InterviewSession session = getSession(data.interviewSessionId());
        validateEventApplicant(session, data.applicantId());

        if (isTranscriptAlreadyProcessed(session.getSessionStatus())) {
            return;
        }
        if (session.getSessionStatus() != InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED) {
            throw new NonRetryableEventException("interview transcript saved event is out of order");
        }
        if (!Boolean.TRUE.equals(data.saved())) {
            throw new NonRetryableEventException("interview transcript was not saved");
        }

        session.updateStatus(InterviewSessionStatus.TRANSCRIPT_SAVED);
        publishReportRequested(session);
        session.updateStatus(InterviewSessionStatus.REPORT_REQUESTED);
    }

    /**
     * AI 최종 평가 결과를 interview_session_id 기준으로 저장하거나 갱신한다.
     *
     * <p>저장 대상은 세 군데다.
     * <ul>
     *   <li>{@code DetailAnalysisReport.skillAnalysis} — {@code evaluation}(criteria_summary, overall_score)</li>
     *   <li>{@code DetailAnalysisReport.cultureAnalysis} — {@code extracted_culturefit}, AI가 보낸
     *       {@code culture_axis}(x/y 좌표), 서버가 파생한 {@code culture_axis_scores}(4축 원점수 평균)</li>
     *   <li>{@code TechnicalSkillReport} 스킬 태그 · {@code CultureReport} 컬처핏 스타일</li>
     * </ul>
     */
    public void handleReportCompleted(InterviewReportCompletedData data) {
        validateReportCompleted(data);

        InterviewSession session = getSession(data.interviewSessionId());
        validateEventApplicant(session, data.applicantId());

        if (session.getSessionStatus() != InterviewSessionStatus.REPORT_REQUESTED
                && session.getSessionStatus() != InterviewSessionStatus.REPORT_COMPLETED) {
            throw new NonRetryableEventException("interview report completed event is out of order");
        }

        Applicant applicant = applicantRepository.findById(data.applicantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        upsertDetailAnalysisReport(applicant, session, data);
        updateTechnicalSkillTags(applicant, data);
        updateCulturefitStyle(applicant, data);

        session.updateStatus(InterviewSessionStatus.REPORT_COMPLETED);
    }

    /**
     * 면접 세션 기준으로 상세 분석 리포트를 upsert한다(재수신 시 같은 행을 갱신해 멱등).
     */
    private void upsertDetailAnalysisReport(
            Applicant applicant,
            InterviewSession session,
            InterviewReportCompletedData data
    ) {
        Map<String, Object> skillAnalysis = new LinkedHashMap<>(data.evaluation());
        Map<String, Object> cultureAnalysis = toCultureAnalysis(data);

        detailAnalysisReportRepository.findByInterviewSession_InterviewSessionId(data.interviewSessionId())
                .ifPresentOrElse(
                        report -> report.updateAnalysis(skillAnalysis, cultureAnalysis),
                        () -> detailAnalysisReportRepository.save(DetailAnalysisReport.builder()
                                .applicant(applicant)
                                .interviewSession(session)
                                .skillAnalysis(skillAnalysis)
                                .cultureAnalysis(cultureAnalysis)
                                .build())
                );
    }

    /**
     * 컬처핏 jsonb payload를 구성한다. AI가 좌표를 보내지 않으면 10개 가치 점수로 직접 계산한다.
     */
    private Map<String, Object> toCultureAnalysis(InterviewReportCompletedData data) {
        Map<String, Double> traits = data.extractedCulturefit();
        CulturefitAxisService.AxisPoint cultureAxis = resolveCultureAxis(data);

        Map<String, Object> axis = new LinkedHashMap<>();
        axis.put("x_axis", cultureAxis.x());
        axis.put("y_axis", cultureAxis.y());

        Map<String, Object> cultureAnalysis = new LinkedHashMap<>();
        cultureAnalysis.put("extracted_culturefit", traits);
        cultureAnalysis.put("culture_axis", axis);
        cultureAnalysis.put("culture_axis_scores", culturefitAxisService.calculateAxisScores(traits));
        return cultureAnalysis;
    }

    /**
     * AI가 보낸 좌표를 쓰고, 누락됐거나 값이 비어 있으면 10개 가치 점수로 직접 계산한다.
     */
    private CulturefitAxisService.AxisPoint resolveCultureAxis(InterviewReportCompletedData data) {
        InterviewReportCompletedData.CultureAxisData cultureAxis = data.cultureAxis();
        if (cultureAxis == null || cultureAxis.xAxis() == null || cultureAxis.yAxis() == null) {
            return culturefitAxisService.calculateAxis(data.extractedCulturefit());
        }
        return new CulturefitAxisService.AxisPoint(cultureAxis.xAxis(), cultureAxis.yAxis());
    }

    /**
     * 면접에서 도출된 스킬 태그와 사용자 제공 태그를 기술 리포트에 반영한다.
     * 프로필 분석이 선행되므로 리포트는 이미 존재하며, 없으면(이례적) 건너뛴다.
     */
    private void updateTechnicalSkillTags(Applicant applicant, InterviewReportCompletedData data) {
        technicalSkillReportRepository.findByApplicant_ApplicantId(applicant.getApplicantId())
                .ifPresent(report -> report.updateSkillTags(data.skillTags(), data.userProvidedTags()));
    }

    /**
     * 컬처핏 좌표가 속한 사분면으로 컬처핏 스타일을 갱신한다. 컬처핏 태그는 프로필 분석 결과를 유지한다.
     */
    private void updateCulturefitStyle(Applicant applicant, InterviewReportCompletedData data) {
        CulturefitStyle style = culturefitAxisService.resolveStyle(resolveCultureAxis(data));

        cultureReportRepository.findByApplicant_ApplicantId(applicant.getApplicantId())
                .ifPresentOrElse(
                        report -> report.updateCulturefitStyle(style),
                        () -> cultureReportRepository.save(CultureReport.builder()
                                .applicant(applicant)
                                .culturefitStyles(style)
                                .build())
                );
    }

    /**
     * 다음 질문 생성 요청 payload를 현재 세션 상태와 마지막 답변 기준으로 구성한다.
     */
    private void publishQuestionRequested(InterviewSession session, InterviewTurnDTO lastTurn, Integer nextSequence) {
        Applicant applicant = session.getApplicant();
        TechnicalSkillReport technicalSkillReport = getCompletedAnalysis(applicant);

        InterviewQuestionRequestedData data = new InterviewQuestionRequestedData(
                applicant.getApplicantId(),
                applicant.getName(),
                session.getInterviewSessionId(),
                lastTurn != null ? lastTurn.questionCode() : null,
                nextSequence,
                technicalSkillReport.getJob(),
                technicalSkillReport.getRole(),
                new InterviewQuestionRequestedData.LastInterviewData(
                        lastTurn != null ? lastTurn.question() : null,
                        lastTurn != null ? lastTurn.answer() : null
                )
        );

        domainEventPublisher.publishAfterCommit(EventEnvelope.request(
                EventType.INTERVIEW_QUESTION_REQUESTED,
                data,
                EventIds.newEventId()
        ));
    }

    private TechnicalSkillReport getCompletedAnalysis(Applicant applicant) {
        TechnicalSkillReport report = technicalSkillReportRepository.findByApplicant(applicant)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_ANALYSIS_NOT_COMPLETED));

        if (!StringUtils.hasText(report.getJob()) || !StringUtils.hasText(report.getRole())) {
            throw new BusinessException(ErrorCode.APPLICANT_ANALYSIS_NOT_COMPLETED);
        }
        return report;
    }

    /**
     * 종료된 면접 transcript를 기술/컬처 섹션으로 나누어 AI 서버 저장 요청 이벤트로 발행한다.
     */
    private void publishTranscriptSaveRequested(InterviewSession session) {
        InterviewTranscriptSaveRequestedData data = new InterviewTranscriptSaveRequestedData(
                session.getApplicant().getApplicantId(),
                session.getInterviewSessionId(),
                new InterviewTranscriptSaveRequestedData.TranscriptSectionData(toTranscriptTurns(session, SKILL_QUESTION_PREFIX)),
                new InterviewTranscriptSaveRequestedData.TranscriptSectionData(toTranscriptTurns(session, CULTURE_QUESTION_PREFIX))
        );

        domainEventPublisher.publishAfterCommit(EventEnvelope.request(
                EventType.INTERVIEW_TRANSCRIPT_SAVE_REQUESTED,
                data,
                EventIds.newEventId()
        ));
    }

    /**
     * 저장된 transcript를 기반으로 AI 서버에 최종 리포트 생성을 요청한다.
     */
    private void publishReportRequested(InterviewSession session) {
        InterviewReportRequestedData data = new InterviewReportRequestedData(
                session.getApplicant().getApplicantId(),
                session.getInterviewSessionId()
        );

        domainEventPublisher.publishAfterCommit(EventEnvelope.request(
                EventType.INTERVIEW_REPORT_REQUESTED,
                data,
                EventIds.newEventId()
        ));
    }

    /**
     * 면접 진행 상태를 연결된 지원자 WebSocket 구독 채널로 전송한다.
     */
    private void sendInterviewMessage(InterviewSession session, InterviewWebSocketMessageResponse response) {
        String applicantPublicId = session.getApplicant().getPublicId();
        runAfterCommit(() -> messagingTemplate.convertAndSendToUser(
                applicantPublicId,
                "/queue/interviews",
                response
        ));
    }

    /**
     * 트랜잭션이 있으면 커밋 후에, 없으면 즉시 실행한다.
     */
    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private List<InterviewTranscriptSaveRequestedData.TranscriptTurnData> toTranscriptTurns(
            InterviewSession session,
            String questionPrefix
    ) {
        return session.getTranscript().stream()
                .filter(Objects::nonNull)
                .filter(turn -> turn.questionCode() != null && turn.questionCode().startsWith(questionPrefix))
                .map(turn -> new InterviewTranscriptSaveRequestedData.TranscriptTurnData(
                        turn.question(),
                        turn.answer()
                ))
                .toList();
    }

    private InterviewSession getSession(UUID interviewSessionId) {
        return interviewSessionRepository.findByInterviewSessionId(interviewSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    }

    private InterviewSession getSessionForApplicant(UUID interviewSessionId, String applicantPublicId) {
        InterviewSession session = getSession(interviewSessionId);
        if (!Objects.equals(session.getApplicant().getPublicId(), applicantPublicId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return session;
    }

    private void validateEventApplicant(InterviewSession session, Long applicantId) {
        if (!Objects.equals(session.getApplicant().getApplicantId(), applicantId)) {
            throw new NonRetryableEventException("applicant_id does not match interview_session_id");
        }
    }

    private void validateQuestionGenerated(InterviewQuestionGeneratedData data) {
        if (data.applicantId() == null) {
            throw new NonRetryableEventException("applicant_id is required");
        }
        if (data.interviewSessionId() == null) {
            throw new NonRetryableEventException("interview_session_id is required");
        }
        if (data.nextQuestionCode() == null || data.nextQuestionCode().isBlank()) {
            throw new NonRetryableEventException("next_question_code is required");
        }
        if (data.sequence() == null) {
            throw new NonRetryableEventException("sequence is required");
        }
        if (data.question() == null || data.question().isBlank()) {
            throw new NonRetryableEventException("question is required");
        }
    }

    private void validateTranscriptSaved(InterviewTranscriptSavedData data) {
        if (data.applicantId() == null) {
            throw new NonRetryableEventException("applicant_id is required");
        }
        if (data.interviewSessionId() == null) {
            throw new NonRetryableEventException("interview_session_id is required");
        }
        if (data.saved() == null) {
            throw new NonRetryableEventException("saved is required");
        }
    }

    private void validateReportCompleted(InterviewReportCompletedData data) {
        if (data.applicantId() == null) {
            throw new NonRetryableEventException("applicant_id is required");
        }
        if (data.interviewSessionId() == null) {
            throw new NonRetryableEventException("interview_session_id is required");
        }
        if (data.evaluation() == null) {
            throw new NonRetryableEventException("evaluation is required");
        }
        if (data.extractedCulturefit() == null || data.extractedCulturefit().isEmpty()) {
            throw new NonRetryableEventException("extracted_culturefit is required");
        }
    }

    private boolean isAnswerClosed(InterviewSessionStatus status) {
        return status == InterviewSessionStatus.FINISHED
                || status == InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED
                || status == InterviewSessionStatus.TRANSCRIPT_SAVED
                || status == InterviewSessionStatus.REPORT_REQUESTED
                || status == InterviewSessionStatus.REPORT_COMPLETED
                || status == InterviewSessionStatus.FAILED;
    }

    /**
     * 이미 분석을 요청한(또는 처리 진행/완료/복구 불가) 상태인지 판정한다.
     * FINISHED(제출 대기)는 포함하지 않고, FAILED는 재제출해도 복구되지 않으므로 포함한다.
     */
    private boolean isSubmitted(InterviewSessionStatus status) {
        return status == InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED
                || status == InterviewSessionStatus.TRANSCRIPT_SAVED
                || status == InterviewSessionStatus.REPORT_REQUESTED
                || status == InterviewSessionStatus.REPORT_COMPLETED
                || status == InterviewSessionStatus.FAILED;
    }

    private boolean isTranscriptAlreadyProcessed(InterviewSessionStatus status) {
        return status == InterviewSessionStatus.TRANSCRIPT_SAVED
                || status == InterviewSessionStatus.REPORT_REQUESTED
                || status == InterviewSessionStatus.REPORT_COMPLETED;
    }

    private boolean isEndQuestion(String questionCode) {
        return questionCode != null && questionCode.startsWith(END_QUESTION_PREFIX);
    }

    private String currentQuarter() {
        LocalDate today = LocalDate.now();
        int quarter = ((today.getMonthValue() - 1) / 3) + 1;
        return today.getYear() + "Q" + quarter;
    }

}
