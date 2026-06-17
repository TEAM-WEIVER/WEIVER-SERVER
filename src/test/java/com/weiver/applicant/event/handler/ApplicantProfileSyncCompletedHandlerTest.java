package com.weiver.applicant.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.analysis.event.ApplicantAnalysisEventService;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.event.dto.ApplicantProfileSyncCompletedData;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ProfileSyncStatus;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicantProfileSyncCompletedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private ApplicantRepository applicantRepository;
    @Mock private ApplicantAnalysisEventService applicantAnalysisEventService;

    @Test
    @DisplayName("프로필 동기화 완료 payload에 synced가 없으면 non-retryable 예외가 발생한다")
    void handle_ThrowsNonRetryable_WhenSyncedIsMissing() {
        ApplicantProfileSyncCompletedHandler handler = new ApplicantProfileSyncCompletedHandler(
                objectMapper,
                applicantRepository,
                applicantAnalysisEventService
        );
        ApplicantProfileSyncCompletedData data = new ApplicantProfileSyncCompletedData(1L, null);

        assertThatThrownBy(() -> handler.handle(envelope(data)))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessage("synced is required");
    }

    @Test
    @DisplayName("이미 동기화 완료된 지원자는 지연된 실패 이벤트로 FAILED 상태가 되지 않는다")
    void handle_DoesNotDowngradeCompletedApplicant() {
        ApplicantProfileSyncCompletedHandler handler = new ApplicantProfileSyncCompletedHandler(
                objectMapper,
                applicantRepository,
                applicantAnalysisEventService
        );
        Applicant applicant = Applicant.builder().applicantId(1L).build();
        applicant.markProfileSyncCompleted(OffsetDateTime.now());

        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));

        handler.handle(envelope(new ApplicantProfileSyncCompletedData(1L, false)));

        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.COMPLETED);
        verify(applicantAnalysisEventService, never()).publishApplicantAnalysisRequested(1L);
    }

    private EventEnvelope<JsonNode> envelope(ApplicantProfileSyncCompletedData data) {
        return new EventEnvelope<>(
                "event-1",
                EventType.APPLICANT_PROFILE_SYNC_COMPLETED,
                null,
                OffsetDateTime.now(),
                EventEnvelope.CURRENT_VERSION,
                objectMapper.valueToTree(data)
        );
    }
}
