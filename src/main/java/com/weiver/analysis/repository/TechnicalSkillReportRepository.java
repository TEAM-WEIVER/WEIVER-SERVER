package com.weiver.analysis.repository;

import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.applicant.domain.Applicant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TechnicalSkillReportRepository extends JpaRepository<TechnicalSkillReport,Long> {
    Optional<TechnicalSkillReport> findByApplicant(Applicant applicant);
    Optional<TechnicalSkillReport> findByApplicant_PublicId(String applicantPublicId);
    Optional<TechnicalSkillReport> findByApplicant_ApplicantId(Long applicantId);
}
