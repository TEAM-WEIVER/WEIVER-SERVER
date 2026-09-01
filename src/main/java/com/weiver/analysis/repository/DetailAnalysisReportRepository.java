package com.weiver.analysis.repository;

import com.weiver.analysis.domain.DetailAnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DetailAnalysisReportRepository extends JpaRepository<DetailAnalysisReport, Long> {
    Optional<DetailAnalysisReport> findTopByApplicant_PublicIdOrderByCreateTimeDesc(String applicantPublicId);
    Optional<DetailAnalysisReport> findByInterviewSession_InterviewSessionId(UUID interviewSessionId);
}
