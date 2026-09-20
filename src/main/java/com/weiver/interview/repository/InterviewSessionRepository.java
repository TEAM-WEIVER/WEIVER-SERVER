package com.weiver.interview.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.type.InterviewSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
