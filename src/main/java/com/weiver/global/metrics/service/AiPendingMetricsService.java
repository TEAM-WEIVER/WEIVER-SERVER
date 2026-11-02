package com.weiver.global.metrics.service;

import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ProfileSyncStatus;
import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewSessionStatus;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JdAnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * AI 결과를 기다리는 건을 flow별로 집계한다. 대기 지표 게이지가 이 결과를 캐시해 내보낸다.
 *
 * <p>flow마다 COUNT 1회와 요청 시각 MIN 1회를 조회한다. 요청 시각이 null인 대기 행(컬럼 추가 전부터
 * 대기 중이던 행)은 건수에는 들어가고, MIN에서 빠지므로 최장 대기 시간 계산에서도 빠진다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AiPendingMetricsService {

    private final JobPostingRepository jobPostingRepository;
    private final ApplicantRepository applicantRepository;
    private final InterviewSessionRepository interviewSessionRepository;

    public Map<AiFlow, AiPendingStat> aggregate() {
        Instant now = Instant.now();
        Map<AiFlow, AiPendingStat> result = new EnumMap<>(AiFlow.class);

        result.put(AiFlow.JD_ANALYSIS, stat(
                jobPostingRepository.countByJdAnalysisStatus(JdAnalysisStatus.REQUESTED),
                offset(jobPostingRepository.findOldestJdAnalysisRequestedAt(JdAnalysisStatus.REQUESTED)),
                now));
        result.put(AiFlow.PROFILE_SYNC, stat(
                applicantRepository.countByProfileSyncStatus(ProfileSyncStatus.REQUESTED),
                offset(applicantRepository.findOldestProfileSyncRequestedAt(ProfileSyncStatus.REQUESTED)),
                now));
        result.put(AiFlow.INTERVIEW_QUESTION, stat(
                interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.WAITING_FOR_QUESTION),
                local(interviewSessionRepository.findOldestQuestionRequestedAt(
                        InterviewSessionStatus.WAITING_FOR_QUESTION)),
                now));
        result.put(AiFlow.INTERVIEW_TRANSCRIPT_SAVE, stat(
                interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED),
                local(interviewSessionRepository.findOldestTranscriptSaveRequestedAt(
                        InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED)),
                now));
        result.put(AiFlow.INTERVIEW_REPORT, stat(
                interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.REPORT_REQUESTED),
                local(interviewSessionRepository.findOldestReportRequestedAt(InterviewSessionStatus.REPORT_REQUESTED)),
                now));

        return Collections.unmodifiableMap(result);
    }

    private AiPendingStat stat(long count, Optional<Instant> oldestRequestedAt, Instant now) {
        double oldestSeconds = oldestRequestedAt
                .map(requestedAt -> Math.max(0, Duration.between(requestedAt, now).toMillis()) / 1000.0)
                .orElse(0.0);
        return new AiPendingStat(count, oldestSeconds);
    }

    private Optional<Instant> offset(Optional<OffsetDateTime> requestedAt) {
        return requestedAt.map(OffsetDateTime::toInstant);
    }

    /** InterviewSession의 요청 시각(LocalDateTime)은 JVM 기본 시간대로 기록하므로 같은 시간대로 바꾼다. */
    private Optional<Instant> local(Optional<LocalDateTime> requestedAt) {
        return requestedAt.map(value -> value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
