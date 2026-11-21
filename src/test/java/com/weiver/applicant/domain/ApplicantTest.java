package com.weiver.applicant.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicantTest {

    @Test
    @DisplayName("제출한 적이 없으면 다음 제출 버전은 1이다")
    void nextSubmissionVersion_NeverSubmitted_IsOne() {
        // given
        Applicant applicant = Applicant.builder().applicantId(1L).build();

        // when & then
        assertThat(applicant.getCurrentSubmissionVersion()).isNull();
        assertThat(applicant.nextSubmissionVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 제출 버전을 가리키면 다음 제출 버전은 그 값에 1을 더한 값이다")
    void pointToSubmission_UpdatesCurrentVersion() {
        // given
        Applicant applicant = Applicant.builder().applicantId(1L).currentSubmissionVersion(1).build();

        // when
        applicant.pointToSubmission(applicant.nextSubmissionVersion());

        // then
        assertThat(applicant.getCurrentSubmissionVersion()).isEqualTo(2);
        assertThat(applicant.nextSubmissionVersion()).isEqualTo(3);
    }
}
