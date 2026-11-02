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
import com.weiver.global.metrics.AiRoundtripMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicantProfileSyncCompletedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private ApplicantRepository applicantRepository;
    @Mock private ApplicantAnalysisEventService applicantAnalysisEventService;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AiRoundtripMetrics aiRoundtripMetrics = new AiRoundtripMetrics(meterRegistry);

    @Test
    @DisplayName("프로필 동기화 완료 payload에 synced가 없으면 non-retryable 예외가 발생한다")
    void handle_ThrowsNonRetryable_WhenSyncedIsMissing() {
        ApplicantProfileSyncCompletedHandler handler = new ApplicantProfileSyncCompletedHandler(
                objectMapper,
                applicantRepository,
                applicantAnalysisEventService,
                aiRoundtripMetrics
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
                applicantAnalysisEventService,
                aiRoundtripMetrics
        );
        Applicant applicant = Applicant.builder().applicantId(1L).build();
        applicant.markProfileSyncCompleted(OffsetDateTime.now());

        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));

        handler.handle(envelope(new ApplicantProfileSyncCompletedData(1L, false)));

        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.COMPLETED);
        verify(applicantAnalysisEventService, never()).publishApplicantAnalysisRequested(1L);
    }

    @Test
    @DisplayName("동기화 성공 결과를 처리하면 요청 시각부터의 지연을 profile_sync/completed로 기록한다")
    void handle_RecordsCompletedRoundtrip_WhenSynced() {
        // given
        ApplicantProfileSyncCompletedHandler handler = handler();
        Applicant applicant = requestedApplicant(OffsetDateTime.now().minusSeconds(30));
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));

        // when
        handler.handle(envelope(new ApplicantProfileSyncCompletedData(1L, true)));

        // then
        Timer timer = roundtrip("completed");
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isCloseTo(30.0, within(5.0));
        assertThat(roundtrip("failed")).isNull();
    }

    @Test
    @DisplayName("동기화 실패 결과(synced=false)를 처리하면 profile_sync/failed로 기록한다")
    void handle_RecordsFailedRoundtrip_WhenNotSynced() {
        // given
        ApplicantProfileSyncCompletedHandler handler = handler();
        Applicant applicant = requestedApplicant(OffsetDateTime.now().minusSeconds(30));
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));

        // when
        handler.handle(envelope(new ApplicantProfileSyncCompletedData(1L, false)));

        // then
        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.FAILED);
        assertThat(roundtrip("failed")).isNotNull();
        assertThat(roundtrip("failed").count()).isEqualTo(1);
        assertThat(roundtrip("completed")).isNull();
    }

    @Test
    @DisplayName("요청 시각이 없는 지원자(컬럼 추가 전 요청)는 결과를 처리해도 지연을 기록하지 않는다")
    void handle_DoesNotRecordRoundtrip_WhenRequestedAtIsNull() {
        // given
        ApplicantProfileSyncCompletedHandler handler = handler();
        Applicant applicant = requestedApplicant(null);
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));

        // when
        handler.handle(envelope(new ApplicantProfileSyncCompletedData(1L, true)));

        // then
        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.COMPLETED);
        assertThat(meterRegistry.find("weiver.ai.roundtrip").timers()).isEmpty();
    }

    @Test
    @DisplayName("이미 동기화 완료된 지원자의 결과 재수신은 지연 타이머에 기록하지 않는다")
    void handle_DoesNotRecordRoundtrip_WhenAlreadyCompleted() {
        // given
        ApplicantProfileSyncCompletedHandler handler = handler();
        Applicant applicant = requestedApplicant(OffsetDateTime.now().minusSeconds(30));
        applicant.markProfileSyncCompleted(OffsetDateTime.now());
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));

        // when
        handler.handle(envelope(new ApplicantProfileSyncCompletedData(1L, true)));

        // then
        assertThat(meterRegistry.find("weiver.ai.roundtrip").timers()).isEmpty();
    }

    private ApplicantProfileSyncCompletedHandler handler() {
        return new ApplicantProfileSyncCompletedHandler(
                objectMapper,
                applicantRepository,
                applicantAnalysisEventService,
                aiRoundtripMetrics
        );
    }

    private Applicant requestedApplicant(OffsetDateTime requestedAt) {
        return Applicant.builder()
                .applicantId(1L)
                .profileSyncStatus(ProfileSyncStatus.REQUESTED)
                .profileSyncRequestedAt(requestedAt)
                .build();
    }

    private Timer roundtrip(String outcome) {
        return meterRegistry.find("weiver.ai.roundtrip")
                .tag("flow", "profile_sync")
                .tag("outcome", outcome)
                .timer();
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
