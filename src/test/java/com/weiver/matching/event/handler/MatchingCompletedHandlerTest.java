package com.weiver.matching.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.event.dto.MatchingCompletedData;
import com.weiver.matching.repository.MatchResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingCompletedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private ApplicantRepository applicantRepository;
    @Mock private MatchResultRepository matchResultRepository;

    @Test
    @DisplayName("매칭 완료 이벤트를 수신하면 기존 결과를 삭제 후 새 결과를 저장한다")
    void handle_ReplacesMatchResults() {
        MatchingCompletedHandler handler = new MatchingCompletedHandler(
                objectMapper,
                jobPostingRepository,
                applicantRepository,
                matchResultRepository
        );
        JobPosting jobPosting = JobPosting.builder().jdId(10L).build();
        Applicant applicant = Applicant.builder().applicantId(1L).build();
        MatchingCompletedData data = new MatchingCompletedData(
                10L,
                List.of(new MatchingCompletedData.MatchData(
                        1L,
                        87.5f,
                        91.0f,
                        89.0f,
                        "요약"
                ))
        );

        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(jobPosting));
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));

        handler.handle(envelope(data));

        InOrder inOrder = inOrder(matchResultRepository);
        inOrder.verify(matchResultRepository).deleteByJobPosting_JdId(10L);
        inOrder.verify(matchResultRepository).flush();
        inOrder.verify(matchResultRepository).saveAll(org.mockito.ArgumentMatchers.any());

        ArgumentCaptor<Iterable<MatchResult>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(matchResultRepository).saveAll(captor.capture());
        List<MatchResult> saved = ((List<MatchResult>) captor.getValue());

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getJobPosting()).isEqualTo(jobPosting);
        assertThat(saved.get(0).getApplicant()).isEqualTo(applicant);
        assertThat(saved.get(0).getSkillScore()).isEqualTo(87.5f);
        assertThat(saved.get(0).getCulturefitScore()).isEqualTo(91.0f);
        assertThat(saved.get(0).getFinalScore()).isEqualTo(89.0f);
        assertThat(saved.get(0).getMatchingRate()).isEqualTo(89.0f);
        assertThat(saved.get(0).getAiSummary()).isEqualTo("요약");
    }

    @Test
    @DisplayName("매칭 완료 payload에 같은 applicant_id가 중복되면 non-retryable 예외가 발생한다")
    void handle_ThrowsNonRetryable_WhenApplicantIdIsDuplicated() {
        MatchingCompletedHandler handler = new MatchingCompletedHandler(
                objectMapper,
                jobPostingRepository,
                applicantRepository,
                matchResultRepository
        );
        MatchingCompletedData data = new MatchingCompletedData(
                10L,
                List.of(
                        new MatchingCompletedData.MatchData(1L, 87.5f, 91.0f, 89.0f, "요약 1"),
                        new MatchingCompletedData.MatchData(1L, 80.0f, 82.0f, 81.0f, "요약 2")
                )
        );

        assertThatThrownBy(() -> handler.handle(envelope(data)))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessage("Duplicate applicant_id in matches: 1");
    }

    private EventEnvelope<JsonNode> envelope(MatchingCompletedData data) {
        return new EventEnvelope<>(
                "event-1",
                EventType.MATCHING_COMPLETED,
                null,
                OffsetDateTime.now(),
                EventEnvelope.CURRENT_VERSION,
                objectMapper.valueToTree(data)
        );
    }
}
