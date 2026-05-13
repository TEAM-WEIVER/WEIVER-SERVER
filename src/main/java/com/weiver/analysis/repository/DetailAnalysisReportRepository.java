package com.weiver.analysis.repository;

import com.weiver.analysis.domain.DetailAnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DetailAnalysisReportRepository extends JpaRepository<DetailAnalysisReport, Long> {
    Optional<DetailAnalysisReport> findByApplicant_PublicId(String applicantPublicId);
}
