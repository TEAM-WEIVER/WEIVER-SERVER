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
import com.weiver.matching.repository.MatchingProcessedEventRepository;
import com.weiver.matching.repository.MatchingProcessingLockRepository;
import com.weiver.matching.service.MatchingResultReplacementService;
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
    @Mock private MatchingProcessedEventRepository processedEventRepository;
    @Mock private MatchingProcessingLockRepository processingLockRepository;

    @Test
    @DisplayName("매칭 완료 이벤트를 수신하면 기존 결과를 삭제 후 새 결과를 저장한다")
    void handle_ReplacesMatchResults() {
        MatchingCompletedHandler handler = new MatchingCompletedHandler(objectMapper,
                new MatchingResultReplacementService(jobPostingRepository, applicantRepository,
                        matchResultRepository, processedEventRepository, processingLockRepository));
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

        given(processedEventRepository.registerIfAbsent("event-1")).willReturn(1);
        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(jobPosting));
        given(applicantRepository.findAllById(List.of(1L))).willReturn(List.of(applicant));

        handler.handle(envelope(data));

        InOrder inOrder = inOrder(matchResultRepository);
        inOrder.verify(matchResultRepository).deleteByJobPosting_JdId(10L);
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
        verify(applicantRepository).findAllById(List.of(1L));
    }

    @Test
    @DisplayName("매칭 완료 payload에 같은 applicant_id가 중복되면 non-retryable 예외가 발생한다")
    void handle_ThrowsNonRetryable_WhenApplicantIdIsDuplicated() {
        MatchingCompletedHandler handler = new MatchingCompletedHandler(objectMapper,
                new MatchingResultReplacementService(jobPostingRepository, applicantRepository,
                        matchResultRepository, processedEventRepository, processingLockRepository));
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

    @Test
    @DisplayName("여러 지원자를 한 번에 조회해 각 매칭 결과에 연결한다")
    void handle_LoadsApplicantsInOneQuery() {
        MatchingCompletedHandler handler = new MatchingCompletedHandler(objectMapper,
                new MatchingResultReplacementService(jobPostingRepository, applicantRepository,
                        matchResultRepository, processedEventRepository, processingLockRepository));
        JobPosting jobPosting = JobPosting.builder().jdId(10L).build();
        Applicant first = Applicant.builder().applicantId(1L).build();
        Applicant second = Applicant.builder().applicantId(2L).build();
        MatchingCompletedData data = new MatchingCompletedData(10L, List.of(
                new MatchingCompletedData.MatchData(1L, 80f, 81f, 82f, "첫째"),
                new MatchingCompletedData.MatchData(2L, 90f, 91f, 92f, "둘째")));
        given(processedEventRepository.registerIfAbsent("event-1")).willReturn(1);
        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(jobPosting));
        given(applicantRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(second, first));

        handler.handle(envelope(data));

        ArgumentCaptor<Iterable<MatchResult>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(matchResultRepository).saveAll(captor.capture());
        List<MatchResult> saved = (List<MatchResult>) captor.getValue();
        assertThat(saved).extracting(MatchResult::getApplicant).containsExactly(first, second);
        verify(applicantRepository).findAllById(List.of(1L, 2L));
    }

    @Test
    @DisplayName("일괄 조회 결과에 지원자가 없으면 기존과 같은 예외가 발생한다")
    void handle_ThrowsWhenApplicantIsMissing() {
        MatchingCompletedHandler handler = new MatchingCompletedHandler(objectMapper,
                new MatchingResultReplacementService(jobPostingRepository, applicantRepository,
                        matchResultRepository, processedEventRepository, processingLockRepository));
        MatchingCompletedData data = new MatchingCompletedData(10L, List.of(
                new MatchingCompletedData.MatchData(1L, 80f, 81f, 82f, "요약")));
        given(processedEventRepository.registerIfAbsent("event-1")).willReturn(1);
        given(jobPostingRepository.findById(10L))
                .willReturn(Optional.of(JobPosting.builder().jdId(10L).build()));
        given(applicantRepository.findAllById(List.of(1L))).willReturn(List.of());

        assertThatThrownBy(() -> handler.handle(envelope(data)))
                .isInstanceOf(com.weiver.global.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(com.weiver.global.exception.ErrorCode.APPLICANT_NOT_FOUND);
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

    @Test
    @DisplayName("처리된 event_id의 재수신은 매칭 조회와 삭제 및 저장을 실행하지 않는다")
    void handle_SkipsProcessedEvent() {
        MatchingCompletedHandler handler = new MatchingCompletedHandler(objectMapper,
                new MatchingResultReplacementService(jobPostingRepository, applicantRepository,
                        matchResultRepository, processedEventRepository, processingLockRepository));
        MatchingCompletedData data = new MatchingCompletedData(10L, List.of());
        given(processedEventRepository.registerIfAbsent("event-1")).willReturn(0);

        handler.handle(envelope(data));

        org.mockito.Mockito.verifyNoInteractions(jobPostingRepository, applicantRepository, matchResultRepository, processingLockRepository);
    }
}
