package com.weiver.interview.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.dto.response.InterviewRemainingResponse;
import com.weiver.interview.dto.response.InterviewTurnDTO;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InterviewSessionService {

    /** 재지원 D-day 계산 기준 타임존(KST). 서버 기본 TZ에 의존하지 않기 위해 고정한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final InterviewSessionRepository interviewSessionRepository;
    private final ApplicantService applicantService;

    /**
     * 지원자의 가장 최근 면접 세션 transcript 전체 조회
     */
    public List<InterviewTurnDTO> getLatestInterviewTurns(String applicantPublicId) {
        Applicant applicant = applicantService.getApplicant(applicantPublicId);

        return interviewSessionRepository.findAllByApplicantOrderByCreateTimeDesc(applicant).stream()
                .findFirst()
                .map(session -> {
                    List<InterviewTurnDTO> transcript = session.getTranscript();
                    if (transcript == null) {
                        return Collections.<InterviewTurnDTO>emptyList();
                    }
                    return transcript;
                })
                .orElseGet(Collections::emptyList);
    }

    /**
     * 로그인 구직자의 AI 면접 잔여 횟수 및 재지원 D-day 조회
     *
     * <p>정책: 면접 결과(분석)를 제출한 시점부터 1개월 동안 면접을 볼 수 없고, 그 시점이 지나면 초기화돼
     * 다시 1회 응시할 수 있다. 기산점은 제출 시각이며 {@code Applicant.nextAvailableScreeningAt}에
     * 기록된다. 한 번도 제출하지 않았으면 항상 응시 가능하다.
     *
     * <p>면접 Q&A만 끝내고(FINISHED) 아직 분석을 제출하지 않은 세션이 있으면 그 세션 ID를 함께 반환해
     * 화면이 제출 버튼을 활성화할 수 있게 한다. 미제출 상태는 쿨다운을 소비하지 않는다.
     *
     * <p>날짜 비교는 서버 기본 TZ에 의존하지 않도록 KST로 고정한다.
     */
    public InterviewRemainingResponse getRemainingInterview(String applicantPublicId) {
        Applicant applicant = applicantService.getApplicant(applicantPublicId);

        LocalDateTime nextAvailableAt = applicant.getNextAvailableScreeningAt();
        if (applicant.isInterviewAvailableAt(LocalDateTime.now())) {
            return InterviewRemainingResponse.available(findPendingSubmissionSessionId(applicant));
        }

        LocalDate today = LocalDate.now(KST);
        LocalDate reapplyDate = nextAvailableAt.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(KST)
                .toLocalDate();
        long dday = ChronoUnit.DAYS.between(today, reapplyDate);

        return InterviewRemainingResponse.waiting(Math.max(0, dday), reapplyDate);
    }

    /**
     * 분석을 아직 제출하지 않은 종료된(FINISHED) 면접 세션 ID. 없으면 null.
     */
    private UUID findPendingSubmissionSessionId(Applicant applicant) {
        return interviewSessionRepository
                .findTopByApplicantAndSessionStatusOrderByCreateTimeDesc(applicant, InterviewSessionStatus.FINISHED)
                .map(InterviewSession::getInterviewSessionId)
                .orElse(null);
    }
}
