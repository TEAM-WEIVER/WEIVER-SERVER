package com.weiver.interview.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.type.InterviewSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    List<InterviewSession> findAllByApplicantOrderByCreateTimeDesc(Applicant applicant);
    Optional<InterviewSession> findByInterviewSessionId(UUID interviewSessionId);

    /** 분석을 아직 요청하지 않은(= 제출 대기 중인) 가장 최근 면접 세션. */
    Optional<InterviewSession> findTopByApplicantAndSessionStatusOrderByCreateTimeDesc(
            Applicant applicant,
            InterviewSessionStatus sessionStatus
    );

    /** 대기 지표용: 해당 상태인 면접 세션 수. */
    long countBySessionStatus(InterviewSessionStatus sessionStatus);

    // 대기 지표용: 상태마다 그 상태의 요청 시각 컬럼에서 가장 이른 값을 찾는다. 요청 시각이 null인 행은 MIN에서 빠진다.

    @Query("SELECT MIN(s.questionRequestedAt) FROM InterviewSession s WHERE s.sessionStatus = :status")
    Optional<LocalDateTime> findOldestQuestionRequestedAt(@Param("status") InterviewSessionStatus status);

    @Query("SELECT MIN(s.transcriptSaveRequestedAt) FROM InterviewSession s WHERE s.sessionStatus = :status")
    Optional<LocalDateTime> findOldestTranscriptSaveRequestedAt(@Param("status") InterviewSessionStatus status);

    @Query("SELECT MIN(s.reportRequestedAt) FROM InterviewSession s WHERE s.sessionStatus = :status")
    Optional<LocalDateTime> findOldestReportRequestedAt(@Param("status") InterviewSessionStatus status);
}
