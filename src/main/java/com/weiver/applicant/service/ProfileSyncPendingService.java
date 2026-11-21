package com.weiver.applicant.service;

import com.weiver.applicant.repository.ApplicantAnalysisSubmissionRepository;
import com.weiver.applicant.type.AnalysisSubmissionStatus;
import com.weiver.global.metrics.AiPendingSource;
import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 지원자 분석 대기 집계. 제출 건 중 REQUESTED의 COUNT 1회와 요청 시각 MIN 1회를 조회한다.
 * 지표 키는 기존 이름(profile_sync)을 유지한다(이름 정리는 #209).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProfileSyncPendingService implements AiPendingSource {

    private final ApplicantAnalysisSubmissionRepository submissionRepository;

    @Override
    public Map<AiFlow, AiPendingStat> collect(Instant now) {
        return Map.of(AiFlow.PROFILE_SYNC, AiPendingStat.of(
                submissionRepository.countByStatus(AnalysisSubmissionStatus.REQUESTED),
                submissionRepository.findOldestRequestedAt(AnalysisSubmissionStatus.REQUESTED)
                        .map(OffsetDateTime::toInstant),
                now));
    }
}
