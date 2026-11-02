package com.weiver.jobposting.service;

import com.weiver.global.metrics.AiPendingSource;
import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JdAnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * JD 분석 대기(jd_analysis) 집계. COUNT 1회와 요청 시각 MIN 1회를 조회한다.
 * 요청 시각이 null인 대기 공고는 건수에는 들어가고 MIN에서 빠진다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class JdAnalysisPendingService implements AiPendingSource {

    private final JobPostingRepository jobPostingRepository;

    @Override
    public Map<AiFlow, AiPendingStat> collect(Instant now) {
        return Map.of(AiFlow.JD_ANALYSIS, AiPendingStat.of(
                jobPostingRepository.countByJdAnalysisStatus(JdAnalysisStatus.REQUESTED),
                jobPostingRepository.findOldestJdAnalysisRequestedAt(JdAnalysisStatus.REQUESTED)
                        .map(OffsetDateTime::toInstant),
                now));
    }
}
