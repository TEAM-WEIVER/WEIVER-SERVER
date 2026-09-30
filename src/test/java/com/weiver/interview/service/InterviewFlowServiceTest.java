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
import com.weiver.interview.event.dto.InterviewReportCompletedData.CultureAxisData;
import com.weiver.interview.event.dto.InterviewTranscriptSaveRequestedData;
import com.weiver.interview.event.dto.InterviewTranscriptSavedData;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InterviewFlowServiceTest {

    private static final String APPLICANT_PUBLIC_ID = "applicant-public-id";

    /** AI payload의 extracted_culturefit 예시(Schwartz 10개 가치, 0~1). */
    private static final Map<String, Double> EXTRACTED_CULTUREFIT = Map.of(
            "자기방향", 0.72,
            "자극", 0.55,
            "쾌락", 0.31,
            "성취", 0.84,
            "권력", 0.22,
            "안전", 0.61,
            "순응", 0.48,
            "전통", 0.35,
            "호의", 0.76,
            "보편주의", 0.69
    );

    @InjectMocks
    private InterviewFlowService interviewFlowService;

    @Mock private ApplicantRepository applicantRepository;
    @Mock private InterviewSessionRepository interviewSessionRepository;
    @Mock private TechnicalSkillReportRepository technicalSkillReportRepository;
    @Mock private DetailAnalysisReportRepository detailAnalysisReportRepository;
    @Mock private CultureReportRepository cultureReportRepository;
    // 순수 계산 컴포넌트라 실제 구현을 주입해 파생 값까지 검증한다.
    @Spy private CulturefitAxisService culturefitAxisService = new CulturefitAxisService();
    @Mock private DomainEventPublisher domainEventPublisher;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private InterviewQuestionVoiceService interviewQuestionVoiceService;

    @Test
    @DisplayName("면접 시작 시 새 세션을 만들고 첫 질문 생성 요청 이벤트를 발행한다")
    void startInterview_CreatesSessionAndPublishesFirstQuestionRequest() {
        Applicant applicant = applicant();
        TechnicalSkillReport technicalSkillReport = TechnicalSkillReport.builder()
                .job("DEVELOPER")
                .role("BACKEND")
                .build();

        given(applicantRepository.findByPublicId(APPLICANT_PUBLIC_ID)).willReturn(Optional.of(applicant));
        given(technicalSkillReportRepository.findByApplicant(applicant)).willReturn(Optional.of(technicalSkillReport));

        InterviewStartResponse response = interviewFlowService.startInterview(
                APPLICANT_PUBLIC_ID,
                new InterviewStartRequest("TECHNICAL")
        );

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository).save(sessionCaptor.capture());
        InterviewSession session = sessionCaptor.getValue();

        ArgumentCaptor<EventEnvelope<?>> eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publishAfterCommit(eventCaptor.capture());

        assertThat(response.interviewSessionId()).isEqualTo(session.getInterviewSessionId());
        assertThat(response.status()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION.name());
        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION);
        assertThat(session.getTranscript()).isEmpty();
        assertThat(session.getQuarter()).matches("\\d{4}Q[1-4]");

        EventEnvelope<?> envelope = eventCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.INTERVIEW_QUESTION_REQUESTED);
        InterviewQuestionRequestedData data = (InterviewQuestionRequestedData) envelope.data();
        assertThat(data.applicantId()).isEqualTo(1L);
        assertThat(data.interviewSessionId()).isEqualTo(session.getInterviewSessionId());
        assertThat(data.sequence()).isEqualTo(1);
        assertThat(data.lastQuestionCode()).isNull();
        assertThat(data.lastInterview().question()).isNull();
        assertThat(data.lastInterview().answer()).isNull();
        assertThat(data.job()).isEqualTo("DEVELOPER");
        assertThat(data.role()).isEqualTo("BACKEND");
    }

    @Test
    @DisplayName("같은 지원자가 면접을 여러 번 시작해도 매번 다른 interview_session_id를 만든다")
    void startInterview_AllowsMultipleSessionsForSameApplicant() {
        Applicant applicant = applicant();
        given(applicantRepository.findByPublicId(APPLICANT_PUBLIC_ID)).willReturn(Optional.of(applicant));
        given(technicalSkillReportRepository.findByApplicant(applicant)).willReturn(Optional.of(completedAnalysis()));

        interviewFlowService.startInterview(APPLICANT_PUBLIC_ID, new InterviewStartRequest("TECHNICAL"));
        interviewFlowService.startInterview(APPLICANT_PUBLIC_ID, new InterviewStartRequest("TECHNICAL"));

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(interviewSessionRepository, times(2)).save(sessionCaptor.capture());

        List<InterviewSession> sessions = sessionCaptor.getAllValues();
        assertThat(sessions.get(0).getInterviewSessionId()).isNotEqualTo(sessions.get(1).getInterviewSessionId());
        verify(domainEventPublisher, times(2)).publishAfterCommit(any());
    }

    @Test
    @DisplayName("지원자 분석이 완료되지 않으면 면접을 시작할 수 없다")
    void startInterview_RejectsWhenApplicantAnalysisIsNotCompleted() {
        Applicant applicant = applicant();
        given(applicantRepository.findByPublicId(APPLICANT_PUBLIC_ID)).willReturn(Optional.of(applicant));
        given(technicalSkillReportRepository.findByApplicant(applicant)).willReturn(Optional.empty());

        assertThatThrownBy(() -> interviewFlowService.startInterview(
                APPLICANT_PUBLIC_ID,
                new InterviewStartRequest("TECHNICAL")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APPLICANT_ANALYSIS_NOT_COMPLETED);

        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("면접 결과를 제출한 뒤 1개월이 지나지 않으면 면접을 시작할 수 없다")
    void startInterview_RejectsWhenCooldownIsNotExpired() {
        Applicant applicant = applicant();
        applicant.markInterviewSubmitted(LocalDateTime.now().minusDays(10));

        given(applicantRepository.findByPublicId(APPLICANT_PUBLIC_ID)).willReturn(Optional.of(applicant));

        assertThatThrownBy(() -> interviewFlowService.startInterview(
                APPLICANT_PUBLIC_ID,
                new InterviewStartRequest("TECHNICAL")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INTERVIEW_COOLDOWN_NOT_EXPIRED);

        verify(interviewSessionRepository, never()).save(any());
        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("면접 결과 제출 후 1개월이 지나면 다시 면접을 시작할 수 있다")
    void startInterview_AllowsAfterCooldownExpired() {
        Applicant applicant = applicant();
        applicant.markInterviewSubmitted(LocalDateTime.now().minusMonths(1).minusDays(1));

        given(applicantRepository.findByPublicId(APPLICANT_PUBLIC_ID)).willReturn(Optional.of(applicant));
        given(technicalSkillReportRepository.findByApplicant(applicant)).willReturn(Optional.of(completedAnalysis()));

        InterviewStartResponse response = interviewFlowService.startInterview(
                APPLICANT_PUBLIC_ID,
                new InterviewStartRequest("TECHNICAL")
        );

        assertThat(response.status()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION.name());
        verify(interviewSessionRepository).save(any());
    }

    @Test
    @DisplayName("답변 제출 시 기존 질문 turn을 overwrite하고 다음 질문 요청 이벤트를 발행한다")
    void submitAnswer_UpdatesTurnAndPublishesNextQuestionRequest() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.QUESTION_READY,
                List.of(InterviewTurnDTO.questionOnly("S_01_00", 1, "첫 질문")));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));
        given(technicalSkillReportRepository.findByApplicant(applicant)).willReturn(Optional.of(completedAnalysis()));

        interviewFlowService.submitAnswer(
                sessionId,
                APPLICANT_PUBLIC_ID,
                new InterviewAnswerSubmitRequest("S_01_00", 1, "첫 답변")
        );

        assertThat(session.getTranscript()).hasSize(1);
        assertThat(session.getTranscript().get(0).answer()).isEqualTo("첫 답변");
        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION);

        ArgumentCaptor<EventEnvelope<?>> eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publishAfterCommit(eventCaptor.capture());

        EventEnvelope<?> envelope = eventCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.INTERVIEW_QUESTION_REQUESTED);
        InterviewQuestionRequestedData data = (InterviewQuestionRequestedData) envelope.data();
        assertThat(data.sequence()).isEqualTo(2);
        assertThat(data.lastQuestionCode()).isEqualTo("S_01_00");
        assertThat(data.lastInterview().question()).isEqualTo("첫 질문");
        assertThat(data.lastInterview().answer()).isEqualTo("첫 답변");
    }

    @Test
    @DisplayName("답변 제출 재시도 중 이미 다음 질문 대기 상태면 다음 질문 요청 이벤트를 재발행하지 않는다")
    void submitAnswer_DoesNotRepublishQuestionRequestWhenAlreadyWaiting() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.WAITING_FOR_QUESTION,
                List.of(new InterviewTurnDTO("S_01_00", 1, "첫 질문", "첫 답변")));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        interviewFlowService.submitAnswer(
                sessionId,
                APPLICANT_PUBLIC_ID,
                new InterviewAnswerSubmitRequest("S_01_00", 1, "수정 답변")
        );

        assertThat(session.getTranscript().get(0).answer()).isEqualTo("수정 답변");
        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.WAITING_FOR_QUESTION);
        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("답변 제출 대상은 questionCode와 sequence가 모두 일치해야 한다")
    void submitAnswer_RequiresExactQuestionCodeAndSequenceMatch() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.QUESTION_READY,
                List.of(InterviewTurnDTO.questionOnly("S_01_00", 1, "첫 질문")));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.submitAnswer(
                sessionId,
                APPLICANT_PUBLIC_ID,
                new InterviewAnswerSubmitRequest("S_01_00", 2, "잘못된 답변")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("답변 대상 질문을 찾을 수 없습니다.");

        assertThat(session.getTranscript().get(0).answer()).isNull();
        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("답변 사전 검증: 질문 대기 상태의 미답변 질문이면 통과한다")
    void validateAnswerTarget_PassesForUnansweredQuestionInReadyState() {
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant(), InterviewSessionStatus.QUESTION_READY,
                List.of(InterviewTurnDTO.questionOnly("S_01_00", 1, "첫 질문")));
        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatNoException().isThrownBy(() ->
                interviewFlowService.validateAnswerTarget(sessionId, APPLICANT_PUBLIC_ID, "S_01_00", 1));
    }

    @Test
    @DisplayName("답변 사전 검증: 다른 지원자의 세션이면 FORBIDDEN 예외가 발생한다")
    void validateAnswerTarget_RejectsOtherApplicantsSession() {
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant(), InterviewSessionStatus.QUESTION_READY,
                List.of(InterviewTurnDTO.questionOnly("S_01_00", 1, "첫 질문")));
        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.validateAnswerTarget(sessionId, "other-public-id", "S_01_00", 1))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("답변 사전 검증: 종료·제출된 세션이면 INTERVIEW_ALREADY_COMPLETED 예외가 발생한다")
    void validateAnswerTarget_RejectsClosedSession() {
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant(), InterviewSessionStatus.FINISHED,
                List.of(new InterviewTurnDTO("S_01_00", 1, "첫 질문", "첫 답변")));
        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.validateAnswerTarget(sessionId, APPLICANT_PUBLIC_ID, "S_01_00", 1))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("답변 사전 검증: 질문 대기 상태가 아니면(이미 답변해 다음 질문 대기 중) INTERVIEW_QUESTION_NOT_READY 예외가 발생한다")
    void validateAnswerTarget_RejectsWhenNotQuestionReady() {
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant(), InterviewSessionStatus.WAITING_FOR_QUESTION,
                List.of(new InterviewTurnDTO("S_01_00", 1, "첫 질문", "첫 답변")));
        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.validateAnswerTarget(sessionId, APPLICANT_PUBLIC_ID, "S_01_00", 1))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.INTERVIEW_QUESTION_NOT_READY);
    }

    @Test
    @DisplayName("답변 사전 검증: 대상 질문(코드·sequence)이 없거나 이미 답변한 질문이면 BAD_REQUEST 예외가 발생한다")
    void validateAnswerTarget_RejectsMissingOrAnsweredTurn() {
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant(), InterviewSessionStatus.QUESTION_READY,
                List.of(
                        new InterviewTurnDTO("S_01_00", 1, "첫 질문", "첫 답변"),
                        InterviewTurnDTO.questionOnly("S_02_00", 2, "둘째 질문")
                ));
        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.validateAnswerTarget(sessionId, APPLICANT_PUBLIC_ID, "S_02_00", 9))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> interviewFlowService.validateAnswerTarget(sessionId, APPLICANT_PUBLIC_ID, "S_01_00", 1))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("이미 저장된 질문 생성 이벤트가 중복 수신되면 transcript에 다시 append하지 않는다")
    void handleQuestionGenerated_IgnoresDuplicateQuestion() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.QUESTION_READY,
                List.of(InterviewTurnDTO.questionOnly("S_01_00", 1, "첫 질문")));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        interviewFlowService.handleQuestionGenerated(new InterviewQuestionGeneratedData(
                1L,
                sessionId,
                "S_01_00",
                1,
                "첫 질문"
        ));

        assertThat(session.getTranscript()).hasSize(1);
        verifyNoInteractions(domainEventPublisher);
        verifyNoInteractions(interviewQuestionVoiceService);
    }

    @Test
    @DisplayName("새 질문이 생성되면 transcript에 append하고 QUESTION_READY 상태로 바꾼 뒤 TTS 작업을 위임한다")
    void handleQuestionGenerated_AppendsQuestionAndDispatchesVoice() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.WAITING_FOR_QUESTION,
                List.of(new InterviewTurnDTO("S_01_00", 1, "첫 질문", "첫 답변")));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        interviewFlowService.handleQuestionGenerated(new InterviewQuestionGeneratedData(
                1L,
                sessionId,
                "S_02_00",
                2,
                "Kafka를 선택한 이유가 무엇인가요?"
        ));

        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.QUESTION_READY);
        assertThat(session.getTranscript()).hasSize(2);

        ArgumentCaptor<InterviewQuestionVoiceService.QuestionVoiceCommand> commandCaptor =
                ArgumentCaptor.forClass(InterviewQuestionVoiceService.QuestionVoiceCommand.class);
        verify(interviewQuestionVoiceService).dispatch(commandCaptor.capture());

        InterviewQuestionVoiceService.QuestionVoiceCommand command = commandCaptor.getValue();
        assertThat(command.interviewSessionId()).isEqualTo(sessionId);
        assertThat(command.applicantPublicId()).isEqualTo(APPLICANT_PUBLIC_ID);
        assertThat(command.questionCode()).isEqualTo("S_02_00");
        assertThat(command.sequence()).isEqualTo(2);
        assertThat(command.question()).isEqualTo("Kafka를 선택한 이유가 무엇인가요?");

        // QUESTION_READY는 TTS 서비스가 푸시하므로 여기서 직접 보내지 않는다.
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("E_ 질문이 생성되면 면접을 종료 상태로만 두고 분석 요청은 발행하지 않는다")
    void handleQuestionGenerated_StopsAtFinishedForEndQuestion() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.WAITING_FOR_QUESTION,
                List.of(
                        new InterviewTurnDTO("S_01_00", 1, "기술 질문", "기술 답변"),
                        new InterviewTurnDTO("C_01_00", 2, "컬처 질문", "컬처 답변")
                ));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        interviewFlowService.handleQuestionGenerated(new InterviewQuestionGeneratedData(
                1L,
                sessionId,
                "E_00_00",
                3,
                "면접이 종료되었습니다."
        ));

        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.FINISHED);
        assertThat(session.getTranscript()).hasSize(3);
        verifyNoInteractions(domainEventPublisher);
        verifyNoInteractions(interviewQuestionVoiceService);

        ArgumentCaptor<InterviewWebSocketMessageResponse> messageCaptor =
                ArgumentCaptor.forClass(InterviewWebSocketMessageResponse.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(APPLICANT_PUBLIC_ID),
                eq("/queue/interviews"),
                messageCaptor.capture()
        );

        InterviewWebSocketMessageResponse message = messageCaptor.getValue();
        assertThat(message.type()).isEqualTo("INTERVIEW_FINISHED");
        assertThat(message.status()).isEqualTo(InterviewSessionStatus.FINISHED.name());
        assertThat(message.interviewSessionId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("구직자가 분석을 요청하면 transcript 저장 요청 이벤트를 발행하고 1개월 재응시 제한을 건다")
    void requestAnalysis_PublishesTranscriptSaveRequestAndStartsCooldown() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.FINISHED,
                List.of(
                        new InterviewTurnDTO("S_01_00", 1, "기술 질문", "기술 답변"),
                        new InterviewTurnDTO("C_01_00", 2, "컬처 질문", "컬처 답변"),
                        new InterviewTurnDTO("E_00_00", 3, "면접이 종료되었습니다.", null)
                ));

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        LocalDateTime before = LocalDateTime.now();
        InterviewAnalysisSubmitResponse response = interviewFlowService.requestAnalysis(sessionId, APPLICANT_PUBLIC_ID);

        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED);
        assertThat(response.interviewSessionId()).isEqualTo(sessionId);
        assertThat(response.status()).isEqualTo(InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED.name());

        // 제출 시각 + 1개월이 재응시 가능 시점으로 기록되고, 그 전에는 면접을 시작할 수 없다.
        assertThat(applicant.getLastScreeningAt()).isAfterOrEqualTo(before);
        assertThat(applicant.getNextAvailableScreeningAt())
                .isEqualTo(applicant.getLastScreeningAt().plusMonths(1));
        assertThat(response.nextAvailableInterviewAt()).isEqualTo(applicant.getNextAvailableScreeningAt());
        assertThat(applicant.isInterviewAvailableAt(LocalDateTime.now())).isFalse();

        ArgumentCaptor<EventEnvelope<?>> eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publishAfterCommit(eventCaptor.capture());

        EventEnvelope<?> envelope = eventCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.INTERVIEW_TRANSCRIPT_SAVE_REQUESTED);
        InterviewTranscriptSaveRequestedData data = (InterviewTranscriptSaveRequestedData) envelope.data();
        assertThat(data.interviewSessionId()).isEqualTo(sessionId);
        assertThat(data.skillInterview().turns()).hasSize(1);
        assertThat(data.skillInterview().turns().get(0).answer()).isEqualTo("기술 답변");
        assertThat(data.cultureInterview().turns()).hasSize(1);
        assertThat(data.cultureInterview().turns().get(0).question()).isEqualTo("컬처 질문");
    }

    @Test
    @DisplayName("면접이 종료되지 않았으면 분석을 요청할 수 없다")
    void requestAnalysis_RejectsWhenInterviewIsNotFinished() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.QUESTION_READY, List.of());

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.requestAnalysis(sessionId, APPLICANT_PUBLIC_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INTERVIEW_NOT_FINISHED);

        assertThat(applicant.getNextAvailableScreeningAt()).isNull();
        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("이미 분석을 요청한 면접은 다시 요청할 수 없다")
    void requestAnalysis_RejectsWhenAnalysisAlreadyRequested() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED, List.of());

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.requestAnalysis(sessionId, APPLICANT_PUBLIC_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INTERVIEW_ANALYSIS_ALREADY_REQUESTED);

        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("복구 불가(FAILED) 세션도 재제출할 수 없다")
    void requestAnalysis_RejectsFailedSession() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.FAILED, List.of());

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.requestAnalysis(sessionId, APPLICANT_PUBLIC_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INTERVIEW_ANALYSIS_ALREADY_REQUESTED);

        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("다른 지원자의 면접 세션에는 분석을 요청할 수 없다")
    void requestAnalysis_RejectsOtherApplicantsSession() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.FINISHED, List.of());

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.requestAnalysis(sessionId, "other-public-id"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("transcript 저장 완료 이벤트 수신 시 report 요청 이벤트를 이어서 발행한다")
    void handleTranscriptSaved_PublishesReportRequest() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED, List.of());

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        interviewFlowService.handleTranscriptSaved(new InterviewTranscriptSavedData(1L, sessionId, true));

        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.REPORT_REQUESTED);

        ArgumentCaptor<EventEnvelope<?>> eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publishAfterCommit(eventCaptor.capture());

        EventEnvelope<?> envelope = eventCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.INTERVIEW_REPORT_REQUESTED);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("transcript 저장 완료 이벤트는 transcript 저장 요청 상태에서만 처리한다")
    void handleTranscriptSaved_RejectsOutOfOrderEvent() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.QUESTION_READY, List.of());

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.handleTranscriptSaved(
                new InterviewTranscriptSavedData(1L, sessionId, true)
        ))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessage("interview transcript saved event is out of order");

        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    @DisplayName("최종 평가 수신 시 evaluation과 컬처핏(10개 가치·좌표·파생 4축)을 리포트에 insert한다")
    void handleReportCompleted_InsertsDetailAnalysisReport() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.REPORT_REQUESTED, List.of());
        Map<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("criteria_summary", Map.of("logic", Map.of("average_score", 4.5)));
        evaluation.put("overall_score", 0.82);

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(detailAnalysisReportRepository.findByInterviewSession_InterviewSessionId(sessionId)).willReturn(Optional.empty());
        given(technicalSkillReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.empty());
        given(cultureReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.empty());

        interviewFlowService.handleReportCompleted(reportCompleted(
                sessionId,
                evaluation,
                new CultureAxisData(0.1234, -0.0567)
        ));

        ArgumentCaptor<DetailAnalysisReport> reportCaptor = ArgumentCaptor.forClass(DetailAnalysisReport.class);
        verify(detailAnalysisReportRepository).save(reportCaptor.capture());

        DetailAnalysisReport saved = reportCaptor.getValue();
        assertThat(saved.getApplicant()).isEqualTo(applicant);
        assertThat(saved.getInterviewSession()).isEqualTo(session);
        assertThat(saved.getSkillAnalysis())
                .containsEntry("overall_score", 0.82)
                .containsKey("criteria_summary");

        assertThat(saved.getCultureAnalysis()).containsEntry("extracted_culturefit", EXTRACTED_CULTUREFIT);
        assertThat(asMap(saved.getCultureAnalysis().get("culture_axis")))
                .containsEntry("x_axis", 0.1234)
                .containsEntry("y_axis", -0.0567);

        // 파생 4축 점수는 축에 속한 가치 원점수의 평균이다(쾌락은 자율·혁신에 포함).
        Map<String, Object> axisScores = asMap(saved.getCultureAnalysis().get("culture_axis_scores"));
        assertThat(((Number) axisScores.get("openness_to_change")).doubleValue())
                .isCloseTo((0.72 + 0.55 + 0.31) / 3, within(1e-9));
        assertThat(((Number) axisScores.get("self_enhancement")).doubleValue())
                .isCloseTo((0.84 + 0.22) / 2, within(1e-9));
        assertThat(((Number) axisScores.get("conservation")).doubleValue())
                .isCloseTo((0.61 + 0.48 + 0.35) / 3, within(1e-9));
        assertThat(((Number) axisScores.get("self_transcendence")).doubleValue())
                .isCloseTo((0.76 + 0.69) / 2, within(1e-9));

        assertThat(session.getSessionStatus()).isEqualTo(InterviewSessionStatus.REPORT_COMPLETED);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("최종 평가 재수신 시 같은 면접 세션의 리포트를 갱신하고 스킬 태그·컬처핏 스타일을 반영한다")
    void handleReportCompleted_UpdatesExistingReportAndApplicantAnalysis() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.REPORT_COMPLETED, List.of());
        DetailAnalysisReport existing = DetailAnalysisReport.builder()
                .applicant(applicant)
                .interviewSession(session)
                .skillAnalysis(Map.of())
                .cultureAnalysis(Map.of())
                .build();
        TechnicalSkillReport technicalSkillReport = TechnicalSkillReport.builder()
                .applicant(applicant)
                .job("DEVELOPER")
                .role("BACKEND")
                .skillTags(List.of("Python"))
                .build();
        CultureReport cultureReport = CultureReport.builder()
                .applicant(applicant)
                .culturefitStyles(CulturefitStyle.STEADY_SUPPORTER)
                .culturefitTag(List.of("주도성"))
                .build();
        Map<String, Object> evaluation = Map.of("criteria_summary", Map.of(), "overall_score", 0.91);

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(detailAnalysisReportRepository.findByInterviewSession_InterviewSessionId(sessionId)).willReturn(Optional.of(existing));
        given(technicalSkillReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.of(technicalSkillReport));
        given(cultureReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.of(cultureReport));

        interviewFlowService.handleReportCompleted(reportCompleted(
                sessionId,
                evaluation,
                new CultureAxisData(0.1234, -0.0567)
        ));

        verify(detailAnalysisReportRepository, never()).save(any());
        assertThat(existing.getSkillAnalysis()).containsEntry("overall_score", 0.91);
        assertThat(existing.getCultureAnalysis()).containsKeys("extracted_culturefit", "culture_axis", "culture_axis_scores");

        // skill_tags · user_provided_tags는 기술 리포트에 반영한다(job/role은 유지).
        assertThat(technicalSkillReport.getSkillTags()).containsExactly("Spring", "Java");
        assertThat(technicalSkillReport.getApplicationProviderTags()).containsExactly("Java");
        assertThat(technicalSkillReport.getJob()).isEqualTo("DEVELOPER");

        // x ≥ 0(자율·혁신) · y < 0(관계·공동체) 사분면 → 포용적 혁신가. 컬처핏 태그는 유지한다.
        assertThat(cultureReport.getCulturefitStyles()).isEqualTo(CulturefitStyle.INCLUSIVE_INNOVATOR);
        assertThat(cultureReport.getCulturefitTag()).containsExactly("주도성");
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("AI가 컬처핏 좌표를 보내지 않으면 10개 가치 점수를 정규화해 좌표를 계산한다")
    void handleReportCompleted_DerivesCultureAxisWhenMissing() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.REPORT_REQUESTED, List.of());

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(detailAnalysisReportRepository.findByInterviewSession_InterviewSessionId(sessionId)).willReturn(Optional.empty());
        given(technicalSkillReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.empty());
        given(cultureReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.empty());

        interviewFlowService.handleReportCompleted(reportCompleted(
                sessionId,
                Map.of("criteria_summary", Map.of()),
                null
        ));

        ArgumentCaptor<DetailAnalysisReport> reportCaptor = ArgumentCaptor.forClass(DetailAnalysisReport.class);
        verify(detailAnalysisReportRepository).save(reportCaptor.capture());

        // 정규화(평균 차감) 후 x = 자율·혁신 - 안정·질서, y = 성과·영향 - 관계·공동체
        double mean = EXTRACTED_CULTUREFIT.values().stream().mapToDouble(Double::doubleValue).sum() / 10;
        double openness = (0.72 + 0.55 + 0.31) / 3 - mean;
        double enhancement = (0.84 + 0.22) / 2 - mean;
        double conservation = (0.61 + 0.48 + 0.35) / 3 - mean;
        double transcendence = (0.76 + 0.69) / 2 - mean;

        Map<String, Object> axis = asMap(reportCaptor.getValue().getCultureAnalysis().get("culture_axis"));
        assertThat(((Number) axis.get("x_axis")).doubleValue()).isCloseTo(openness - conservation, within(1e-9));
        assertThat(((Number) axis.get("y_axis")).doubleValue()).isCloseTo(enhancement - transcendence, within(1e-9));
    }

    @Test
    @DisplayName("report 완료 이벤트는 report 요청 이후 상태에서만 처리한다")
    void handleReportCompleted_RejectsOutOfOrderEvent() {
        Applicant applicant = applicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession session = session(sessionId, applicant, InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED, List.of());

        given(interviewSessionRepository.findByInterviewSessionId(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> interviewFlowService.handleReportCompleted(reportCompleted(
                sessionId,
                Map.of("criteria_summary", Map.of()),
                new CultureAxisData(0.1, 0.1)
        )))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessage("interview report completed event is out of order");

        verifyNoInteractions(detailAnalysisReportRepository);
    }

    @Test
    @DisplayName("extracted_culturefit이 비어 있으면 재처리 불가 예외로 거절한다")
    void handleReportCompleted_RejectsMissingExtractedCulturefit() {
        UUID sessionId = UUID.randomUUID();

        assertThatThrownBy(() -> interviewFlowService.handleReportCompleted(new InterviewReportCompletedData(
                1L,
                sessionId,
                "홍길동",
                List.of("Spring"),
                List.of("Java"),
                Map.of("criteria_summary", Map.of()),
                Map.of(),
                new CultureAxisData(0.1, 0.1)
        )))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessage("extracted_culturefit is required");

        verifyNoInteractions(interviewSessionRepository);
    }

    private InterviewReportCompletedData reportCompleted(
            UUID sessionId,
            Map<String, Object> evaluation,
            CultureAxisData cultureAxis
    ) {
        return new InterviewReportCompletedData(
                1L,
                sessionId,
                "홍길동",
                List.of("Spring", "Java"),
                List.of("Java"),
                evaluation,
                EXTRACTED_CULTUREFIT,
                cultureAxis
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Applicant applicant() {
        return Applicant.builder()
                .applicantId(1L)
                .publicId(APPLICANT_PUBLIC_ID)
                .name("홍길동")
                .build();
    }

    private TechnicalSkillReport completedAnalysis() {
        return TechnicalSkillReport.builder()
                .job("DEVELOPER")
                .role("BACKEND")
                .build();
    }

    private InterviewSession session(
            UUID sessionId,
            Applicant applicant,
            InterviewSessionStatus status,
            List<InterviewTurnDTO> transcript
    ) {
        return InterviewSession.builder()
                .interviewSessionId(sessionId)
                .applicant(applicant)
                .quarter("2026Q2")
                .sessionStatus(status)
                .transcript(transcript)
                .build();
    }
}
