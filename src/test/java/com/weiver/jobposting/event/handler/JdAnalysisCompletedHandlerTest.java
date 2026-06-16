package com.weiver.jobposting.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.analysis.domain.JdAnalysisResult;
import com.weiver.analysis.repository.JdAnalysisResultRepository;
import com.weiver.company.domain.Company;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.event.dto.JdAnalysisCompletedData;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JdAnalysisStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JdAnalysisCompletedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private JdAnalysisResultRepository jdAnalysisResultRepository;

    @Test
    @DisplayName("JD 분석 완료 이벤트를 수신하면 JD 분석 결과를 저장하고 공고 상태를 COMPLETED로 바꾼다")
    void handle_SavesJdAnalysisResultAndMarksCompleted() {
        JdAnalysisCompletedHandler handler = new JdAnalysisCompletedHandler(
                objectMapper,
                jobPostingRepository,
                jdAnalysisResultRepository
        );
        Company company = Company.builder().companyId(2L).build();
        JobPosting jobPosting = JobPosting.builder()
                .jdId(10L)
                .company(company)
                .build();
        JdAnalysisCompletedData data = new JdAnalysisCompletedData(
                10L,
                2L,
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
