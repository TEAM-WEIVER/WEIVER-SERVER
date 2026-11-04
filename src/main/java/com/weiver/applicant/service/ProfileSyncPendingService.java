package com.weiver.applicant.service;

import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ProfileSyncStatus;
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
 * 프로필 동기화 대기(profile_sync) 집계. COUNT 1회와 요청 시각 MIN 1회를 조회한다.
 * 요청 시각이 null인 대기 지원자는 건수에는 들어가고 MIN에서 빠진다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProfileSyncPendingService implements AiPendingSource {

    private final ApplicantRepository applicantRepository;

    @Override
    public Map<AiFlow, AiPendingStat> collect(Instant now) {
        return Map.of(AiFlow.PROFILE_SYNC, AiPendingStat.of(
                applicantRepository.countByProfileSyncStatus(ProfileSyncStatus.REQUESTED),
                applicantRepository.findOldestProfileSyncRequestedAt(ProfileSyncStatus.REQUESTED)
                        .map(OffsetDateTime::toInstant),
                now));
    }
}
