package com.weiver.interview.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.dto.response.InterviewRemainingResponse;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InterviewSessionServiceTest {

    @Mock
    private InterviewSessionRepository interviewSessionRepository;
    @Mock
    private ApplicantService applicantService;
    @InjectMocks
    private InterviewSessionService interviewSessionService;

    private static final String PUBLIC_ID = "applicant-public-id";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private Applicant anApplicant() {
        return Applicant.builder().build();
    }

    /**
     * 면접 결과를 KST 기준 특정 날짜(정오)에 제출한 지원자를 만든다.
     * 서버 기본 TZ와 무관하게 서비스가 해당 KST 날짜로 환산하도록 제출 시각을 구성한다.
     */
    private Applicant anApplicantSubmittedOnKstDate(LocalDate kstDate) {
        Applicant applicant = anApplicant();
        applicant.markInterviewSubmitted(ZonedDateTime.of(kstDate, LocalTime.NOON, KST)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime());
        return applicant;
    }

    @Test
    @DisplayName("면접 결과를 한 번도 제출하지 않았으면 면접 가능(remainingCount 1, D-day 0, 재지원일 null)이다")
    void getRemainingInterview_NeverSubmitted() {
        // given
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicant());
        given(interviewSessionRepository.findTopByApplicantAndSessionStatusOrderByCreateTimeDesc(
                any(Applicant.class), eq(InterviewSessionStatus.FINISHED)))
                .willReturn(Optional.empty());

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.remainingCount()).isEqualTo(1);
        assertThat(response.reapplyDDay()).isZero();
        assertThat(response.reapplyAvailableDate()).isNull();
        assertThat(response.pendingSubmissionSessionId()).isNull();
    }

    @Test
    @DisplayName("10일 전에 제출했으면 잔여 0이고 재지원일은 제출일 + 1개월이다")
    void getRemainingInterview_SubmittedTenDaysAgo() {
        // given
        LocalDate today = LocalDate.now(KST);
        LocalDate submittedDate = today.minusDays(10);
        LocalDate expectedReapplyDate = submittedDate.plusMonths(1);
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicantSubmittedOnKstDate(submittedDate));

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.remainingCount()).isZero();
        assertThat(response.reapplyAvailableDate()).isEqualTo(expectedReapplyDate);
        assertThat(response.reapplyDDay())
                .isEqualTo(ChronoUnit.DAYS.between(today, expectedReapplyDate));
        assertThat(response.pendingSubmissionSessionId()).isNull();
    }

    @Test
    @DisplayName("제출 후 1개월이 지나면 쿨다운이 초기화돼 다시 면접 가능하다")
    void getRemainingInterview_CooldownExpired() {
        // given
        LocalDate submittedDate = LocalDate.now(KST).minusMonths(1).minusDays(1);
        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(anApplicantSubmittedOnKstDate(submittedDate));
        given(interviewSessionRepository.findTopByApplicantAndSessionStatusOrderByCreateTimeDesc(
                any(Applicant.class), eq(InterviewSessionStatus.FINISHED)))
                .willReturn(Optional.empty());

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.remainingCount()).isEqualTo(1);
        assertThat(response.reapplyDDay()).isZero();
        assertThat(response.reapplyAvailableDate()).isNull();
    }

    @Test
    @DisplayName("면접 Q&A만 끝내고 분석을 제출하지 않은 세션이 있으면 해당 세션 ID를 함께 반환한다")
    void getRemainingInterview_ReturnsPendingSubmissionSessionId() {
        // given
        Applicant applicant = anApplicant();
        UUID sessionId = UUID.randomUUID();
        InterviewSession pending = InterviewSession.builder()
                .interviewSessionId(sessionId)
                .applicant(applicant)
                .quarter("2026Q3")
                .sessionStatus(InterviewSessionStatus.FINISHED)
                .build();

        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(applicant);
        given(interviewSessionRepository.findTopByApplicantAndSessionStatusOrderByCreateTimeDesc(
                eq(applicant), eq(InterviewSessionStatus.FINISHED)))
                .willReturn(Optional.of(pending));

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.remainingCount()).isEqualTo(1);
        assertThat(response.pendingSubmissionSessionId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("경계: 제출일 + 1개월이 오늘이면 쿨다운이 끝나 면접 가능하다")
    void getRemainingInterview_ExactlyOneMonthAgoIsAvailable() {
        // given
        LocalDate submittedDate = LocalDate.now(KST).minusMonths(1);
        Applicant applicant = anApplicant();
        // 제출 시각 + 1개월이 이미 지난 시점이 되도록 정오가 아닌 자정 기준으로 제출했다고 본다.
        applicant.markInterviewSubmitted(ZonedDateTime.of(submittedDate, LocalTime.MIDNIGHT, KST)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime());

        given(applicantService.getApplicant(PUBLIC_ID)).willReturn(applicant);
        given(interviewSessionRepository.findTopByApplicantAndSessionStatusOrderByCreateTimeDesc(
                any(Applicant.class), eq(InterviewSessionStatus.FINISHED)))
                .willReturn(Optional.empty());

        // when
        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(PUBLIC_ID);

        // then
        assertThat(response.remainingCount()).isEqualTo(1);
        assertThat(response.reapplyAvailableDate()).isNull();
    }
}
