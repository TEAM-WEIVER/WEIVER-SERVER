package com.weiver.matching.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.event.dto.MatchingCompletedData;
import com.weiver.matching.repository.MatchResultRepository;
import com.weiver.matching.repository.MatchingProcessedEventRepository;
import com.weiver.matching.repository.MatchingProcessingLockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MatchingResultReplacementServiceTest {

    @InjectMocks private MatchingResultReplacementService service;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private ApplicantRepository applicantRepository;
    @Mock private MatchResultRepository matchResultRepository;
    @Mock private MatchingProcessedEventRepository processedEventRepository;
    @Mock private MatchingProcessingLockRepository processingLockRepository;
    @Captor private ArgumentCaptor<List<MatchResult>> resultsCaptor;

    @Test
    @DisplayName("지원자를 일괄 조회하고 입력 순서로 점수·요약을 연결해 잠금 안에서 결과를 교체한다")
    void replace_LoadsApplicantsAndReplacesResults() {
        // given
        JobPosting job = JobPosting.builder().jdId(10L).build();
        Applicant first = Applicant.builder().applicantId(1L).build();
        Applicant second = Applicant.builder().applicantId(2L).build();
        var data = new MatchingCompletedData(10L, List.of(
                new MatchingCompletedData.MatchData(1L, 80f, 81f, 82f, "첫째"),
                new MatchingCompletedData.MatchData(2L, 90f, 91f, 92f, "둘째")));
        given(processedEventRepository.registerIfAbsent("event-1")).willReturn(1);
        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(job));
        given(applicantRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(second, first));

        // when
        service.replace("event-1", data);

        // then
        var order = inOrder(processedEventRepository, processingLockRepository, jobPostingRepository,
                applicantRepository, matchResultRepository);
        order.verify(processedEventRepository).registerIfAbsent("event-1");
        order.verify(processingLockRepository).lock();
        order.verify(jobPostingRepository).findById(10L);
        order.verify(applicantRepository).findAllById(List.of(1L, 2L));
        order.verify(matchResultRepository).bulkDeleteByJdId(10L);
        order.verify(matchResultRepository).saveAll(resultsCaptor.capture());
        List<MatchResult> saved = resultsCaptor.getValue();
        assertThat(saved).extracting(MatchResult::getApplicant).containsExactly(first, second);
        assertThat(saved).allSatisfy(result -> assertThat(result.getJobPosting()).isSameAs(job));
        assertThat(saved).extracting(MatchResult::getSkillScore).containsExactly(80f, 90f);
        assertThat(saved).extracting(MatchResult::getCulturefitScore).containsExactly(81f, 91f);
        assertThat(saved).extracting(MatchResult::getFinalScore).containsExactly(82f, 92f);
        assertThat(saved).extracting(MatchResult::getMatchingRate).containsExactly(82f, 92f);
        assertThat(saved).extracting(MatchResult::getAiSummary).containsExactly("첫째", "둘째");
    }

    @Test
    @DisplayName("처리된 이벤트는 잠금·조회·삭제·저장을 실행하지 않는다")
    void replace_SkipsProcessedEvent() {
        // given
        given(processedEventRepository.registerIfAbsent("event-1")).willReturn(0);

        // when
        service.replace("event-1", new MatchingCompletedData(10L, List.of()));

        // then
        verifyNoInteractions(processingLockRepository, jobPostingRepository, applicantRepository, matchResultRepository);
    }

    @Test
    @DisplayName("지원자가 누락되면 기존 결과를 삭제하기 전에 APPLICANT_NOT_FOUND로 실패한다")
    void replace_RejectsMissingApplicantBeforeDeletion() {
        // given
        var data = new MatchingCompletedData(10L,
                List.of(new MatchingCompletedData.MatchData(1L, 80f, 81f, 82f, "요약")));
        given(processedEventRepository.registerIfAbsent("event-1")).willReturn(1);
        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(JobPosting.builder().jdId(10L).build()));
        given(applicantRepository.findAllById(List.of(1L))).willReturn(List.of());

        // when / then
        assertThatThrownBy(() -> service.replace("event-1", data))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);
        verifyNoInteractions(matchResultRepository);
    }

    @Test
    @DisplayName("공고가 없으면 JOB_POSTING_NOT_FOUND로 실패한다")
    void replace_RejectsMissingJobPosting() {
        // given
        given(processedEventRepository.registerIfAbsent("event-1")).willReturn(1);
        given(jobPostingRepository.findById(10L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.replace("event-1", new MatchingCompletedData(10L, List.of())))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(ErrorCode.JOB_POSTING_NOT_FOUND);
        verifyNoInteractions(applicantRepository, matchResultRepository);
    }

    @Test
    @DisplayName("빈 매칭 목록은 기존 결과를 삭제하고 빈 목록으로 교체한다")
    void replace_ClearsResultsForEmptyMatches() {
        // given
        given(processedEventRepository.registerIfAbsent("event-1")).willReturn(1);
        given(jobPostingRepository.findById(10L)).willReturn(Optional.of(JobPosting.builder().jdId(10L).build()));
        given(applicantRepository.findAllById(List.of())).willReturn(List.of());

        // when
        service.replace("event-1", new MatchingCompletedData(10L, List.of()));

        // then
        verify(matchResultRepository).bulkDeleteByJdId(10L);
        verify(matchResultRepository).saveAll(resultsCaptor.capture());
        assertThat(resultsCaptor.getValue()).isEmpty();
    }
}
