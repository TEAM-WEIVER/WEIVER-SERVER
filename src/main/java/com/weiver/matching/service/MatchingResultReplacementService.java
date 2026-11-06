package com.weiver.matching.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.event.dto.MatchingCompletedData;
import com.weiver.matching.repository.MatchingProcessedEventRepository;
import com.weiver.matching.repository.MatchResultRepository;
import com.weiver.matching.repository.MatchingProcessingLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class MatchingResultReplacementService {

    private final JobPostingRepository jobPostingRepository;
    private final ApplicantRepository applicantRepository;
    private final MatchResultRepository matchResultRepository;
    private final MatchingProcessedEventRepository processedEventRepository;
    private final MatchingProcessingLockRepository processingLockRepository;

    public void replace(String eventId, MatchingCompletedData data) {
        if (processedEventRepository.registerIfAbsent(eventId) == 0) {
            return;
        }

        processingLockRepository.lock();
        JobPosting jobPosting = jobPostingRepository.findById(data.jdId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));
        List<Long> applicantIds = data.matches().stream()
                .map(MatchingCompletedData.MatchData::applicantId)
                .toList();
        Map<Long, Applicant> applicantsById = applicantRepository.findAllById(applicantIds).stream()
                .collect(Collectors.toMap(Applicant::getApplicantId, Function.identity()));
        List<MatchResult> matchResults = data.matches().stream()
                .map(match -> toMatchResult(jobPosting, match, applicantsById))
                .toList();

        matchResultRepository.deleteByJobPosting_JdId(jobPosting.getJdId());
        matchResultRepository.saveAll(matchResults);
    }

    private MatchResult toMatchResult(JobPosting jobPosting, MatchingCompletedData.MatchData match,
                                      Map<Long, Applicant> applicantsById) {
        Applicant applicant = applicantsById.get(match.applicantId());
        if (applicant == null) {
            throw new BusinessException(ErrorCode.APPLICANT_NOT_FOUND);
        }

        return MatchResult.builder()
                .jobPosting(jobPosting)
                .applicant(applicant)
                .skillScore(match.skillScore())
                .culturefitScore(match.cultureScore())
                .finalScore(match.finalScore())
                .matchingRate(match.finalScore())
                .aiSummary(match.reason())
                .build();
    }
}
