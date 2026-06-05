package com.weiver.interview.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.interview.domain.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    Optional<InterviewSession> findByApplicant_PublicId(String applicantPublicId);
    List<InterviewSession> findAllByApplicantOrderByCreateTimeDesc(Applicant applicant);
    Optional<InterviewSession> findByInterviewSessionId(UUID interviewSessionId);
    boolean existsByInterviewSessionId(UUID interviewSessionId);
}
