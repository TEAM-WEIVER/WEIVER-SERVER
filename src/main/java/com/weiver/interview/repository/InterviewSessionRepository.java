package com.weiver.interview.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.type.InterviewType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    Optional<InterviewSession> findByApplicant_PublicId(String applicantPublicId);
    Optional<InterviewSession> findByApplicantAndInterviewType(Applicant applicant, InterviewType interviewType);
}
