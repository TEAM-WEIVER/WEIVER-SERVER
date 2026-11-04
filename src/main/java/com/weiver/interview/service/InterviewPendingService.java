package com.weiver.interview.service;

import com.weiver.global.metrics.AiPendingSource;
import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * 면접 대기(interview_question / interview_transcript_save / interview_report) 집계.
 * 상태마다 COUNT 1회와 그 상태의 요청 시각 컬럼 MIN 1회를 조회한다.
 * 요청 시각이 null인 대기 세션은 건수에는 들어가고 MIN에서 빠진다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InterviewPendingService implements AiPendingSource {

    private final InterviewSessionRepository interviewSessionRepository;

    @Override
    public Map<AiFlow, AiPendingStat> collect(Instant now) {
        return Map.of(
                AiFlow.INTERVIEW_QUESTION, AiPendingStat.of(
                        interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.WAITING_FOR_QUESTION),
                        toInstant(interviewSessionRepository.findOldestQuestionRequestedAt(
                                InterviewSessionStatus.WAITING_FOR_QUESTION)),
                        now),
                AiFlow.INTERVIEW_TRANSCRIPT_SAVE, AiPendingStat.of(
                        interviewSessionRepository.countBySessionStatus(
                                InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED),
                        toInstant(interviewSessionRepository.findOldestTranscriptSaveRequestedAt(
                                InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED)),
                        now),
                AiFlow.INTERVIEW_REPORT, AiPendingStat.of(
                        interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.REPORT_REQUESTED),
                        toInstant(interviewSessionRepository.findOldestReportRequestedAt(
                                InterviewSessionStatus.REPORT_REQUESTED)),
                        now));
    }

    /** 요청 시각(LocalDateTime)은 JVM 기본 시간대로 기록하므로 같은 시간대로 바꾼다. */
    private Optional<Instant> toInstant(Optional<LocalDateTime> requestedAt) {
        return requestedAt.map(value -> value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
