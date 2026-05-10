package com.weiver.analysis.repository;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.applicant.domain.Applicant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CultureReportRepository extends JpaRepository<CultureReport, Long> {
    Optional<CultureReport> findByApplicant(Applicant applicant);
    Optional<CultureReport> findCultureReportForContact(Long jdId, String applicantPublicId, String companyPublicId);
}
