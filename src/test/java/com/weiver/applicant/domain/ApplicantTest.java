package com.weiver.applicant.domain;

import com.weiver.applicant.type.ProfileSyncStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicantTest {

    @Test
    @DisplayName("프로필 동기화를 요청하면 REQUESTED 상태가 되고 요청 시각이 기록된다")
    void markProfileSyncRequested_RecordsRequestedAt() {
        // given
        Applicant applicant = Applicant.builder().applicantId(1L).build();
        OffsetDateTime before = OffsetDateTime.now();

        // when
        applicant.markProfileSyncRequested();

        // then
        OffsetDateTime after = OffsetDateTime.now();
        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.REQUESTED);
        assertThat(applicant.getProfileSyncRequestedAt()).isNotNull();
        assertThat(applicant.getProfileSyncRequestedAt()).isBetween(before, after);
    }
}
