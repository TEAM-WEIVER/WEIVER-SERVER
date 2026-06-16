package com.weiver.matching.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.event.consumer.DomainEventHandler;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.event.dto.MatchingCompletedData;
import com.weiver.matching.repository.MatchResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MatchingCompletedHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final JobPostingRepository jobPostingRepository;
    private final ApplicantRepository applicantRepository;
    private final MatchResultRepository matchResultRepository;

    @Override
    public EventType support() {
        return EventType.MATCHING_COMPLETED;
    }

    @Override
    @Transactional
    public void handle(EventEnvelope<JsonNode> envelope) {
        // 같은 공고의 기존 매칭 결과를 지우고 AI가 내려준 최신 결과로 교체한다.
        MatchingCompletedData data = objectMapper.convertValue(
                envelope.data(),
                MatchingCompletedData.class
        );
        if (data.matches() == null) {
            throw new NonRetryableEventException("matches is required");
        }

        JobPosting jobPosting = jobPostingRepository.findById(data.jdId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        matchResultRepository.deleteByJobPosting_JdId(jobPosting.getJdId());
        matchResultRepository.flush();

        List<MatchResult> matchResults = data.matches().stream()
                .map(match -> toMatchResult(jobPosting, match))
                .toList();

        matchResultRepository.saveAll(matchResults);
    }

    private MatchResult toMatchResult(JobPosting jobPosting, MatchingCompletedData.MatchData match) {
        Applicant applicant = applicantRepository.findById(match.applicantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

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
