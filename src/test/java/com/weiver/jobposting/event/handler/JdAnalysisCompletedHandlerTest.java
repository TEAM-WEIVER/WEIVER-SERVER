package com.weiver.jobposting.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.analysis.domain.JdAnalysisResult;
import com.weiver.analysis.repository.JdAnalysisResultRepository;
import com.weiver.company.domain.Company;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.metrics.AiRoundtripMetrics;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.event.dto.JdAnalysisCompletedData;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JdAnalysisStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JdAnalysisCompletedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private JdAnalysisResultRepository jdAnalysisResultRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AiRoundtripMetrics aiRoundtripMetrics = new AiRoundtripMetrics(meterRegistry);

    @Test
    @DisplayName("JD 분석 완료 이벤트를 수신하면 JD 분석 결과를 저장하고 공고 상태를 COMPLETED로 바꾼다")
    void handle_SavesJdAnalysisResultAndMarksCompleted() {
        JdAnalysisCompletedHandler handler = new JdAnalysisCompletedHandler(
                objectMapper,
                jobPostingRepository,
                jdAnalysisResultRepository,
                aiRoundtripMetrics
        );
        Company company = Company.builder().companyId(2L).build();
        JobPosting jobPosting = JobPosting.builder()
                .jdId(10L)
                .company(company)
                .build();
        JdAnalysisCompletedData data = new JdAnalysisCompletedData(
                10L,
                999L,
                "original jd text",
                List.of(0.1, 0.2, 0.3)
        );

        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(jobPosting));
        given(jdAnalysisResultRepository.findByJobPosting_JdId(10L)).willReturn(Optional.empty());

        handler.handle(envelope(data));

        ArgumentCaptor<JdAnalysisResult> captor = ArgumentCaptor.forClass(JdAnalysisResult.class);
        verify(jdAnalysisResultRepository).save(captor.capture());

        JdAnalysisResult saved = captor.getValue();
        assertThat(saved.getJobPosting()).isEqualTo(jobPosting);
        assertThat(saved.getCompanyId()).isEqualTo(2L);
        assertThat(saved.getOriginalText()).isEqualTo("original jd text");
        assertThat(saved.getEmbedding()).containsExactly(0.1, 0.2, 0.3);
        assertThat(jobPosting.getJdAnalysisStatus()).isEqualTo(JdAnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("분석 요청 중인 공고의 결과를 처리하면 요청 시각부터의 지연을 jd_analysis/completed로 기록한다")
    void handle_RecordsRoundtrip_WhenRequested() {
        // given
        JdAnalysisCompletedHandler handler = handler();
        JobPosting jobPosting = jobPosting(JdAnalysisStatus.REQUESTED, OffsetDateTime.now().minusSeconds(45));
        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(jobPosting));
        given(jdAnalysisResultRepository.findByJobPosting_JdId(10L)).willReturn(Optional.empty());

        // when
        handler.handle(envelope(data()));

        // then
        Timer timer = meterRegistry.get("weiver.ai.roundtrip")
                .tag("flow", "jd_analysis")
                .tag("outcome", "completed")
                .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isCloseTo(45.0, within(5.0));
    }

    @Test
    @DisplayName("요청 시각이 없는 공고(컬럼 추가 전 요청)는 결과를 처리해도 지연을 기록하지 않는다")
    void handle_DoesNotRecordRoundtrip_WhenRequestedAtIsNull() {
        // given
        JdAnalysisCompletedHandler handler = handler();
        JobPosting jobPosting = jobPosting(JdAnalysisStatus.REQUESTED, null);
        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(jobPosting));
        given(jdAnalysisResultRepository.findByJobPosting_JdId(10L)).willReturn(Optional.empty());

        // when
        handler.handle(envelope(data()));

        // then
        assertThat(jobPosting.getJdAnalysisStatus()).isEqualTo(JdAnalysisStatus.COMPLETED);
        assertThat(meterRegistry.find("weiver.ai.roundtrip").timers()).isEmpty();
    }

    @Test
    @DisplayName("이미 분석 완료된 공고의 결과 재수신은 지연 타이머에 다시 기록하지 않는다")
    void handle_DoesNotRecordRoundtrip_WhenAlreadyCompleted() {
        // given
        JdAnalysisCompletedHandler handler = handler();
        JobPosting jobPosting = jobPosting(JdAnalysisStatus.COMPLETED, OffsetDateTime.now().minusSeconds(45));
        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(jobPosting));
        given(jdAnalysisResultRepository.findByJobPosting_JdId(10L)).willReturn(Optional.empty());

        // when
        handler.handle(envelope(data()));

        // then
        assertThat(meterRegistry.find("weiver.ai.roundtrip").timers()).isEmpty();
    }

    private JdAnalysisCompletedHandler handler() {
        return new JdAnalysisCompletedHandler(
                objectMapper,
                jobPostingRepository,
                jdAnalysisResultRepository,
                aiRoundtripMetrics
        );
    }

    private JobPosting jobPosting(JdAnalysisStatus status, OffsetDateTime requestedAt) {
        return JobPosting.builder()
                .jdId(10L)
                .company(Company.builder().companyId(2L).build())
                .jdAnalysisStatus(status)
                .jdAnalysisRequestedAt(requestedAt)
                .build();
    }

    private JdAnalysisCompletedData data() {
        return new JdAnalysisCompletedData(10L, 2L, "original jd text", List.of(0.1));
    }

    private EventEnvelope<JsonNode> envelope(JdAnalysisCompletedData data) {
        return new EventEnvelope<>(
                "event-1",
                EventType.JD_ANALYSIS_COMPLETED,
                null,
                OffsetDateTime.now(),
                EventEnvelope.CURRENT_VERSION,
                objectMapper.valueToTree(data)
        );
    }
}
