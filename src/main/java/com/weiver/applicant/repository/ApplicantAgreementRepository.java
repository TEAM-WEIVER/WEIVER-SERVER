package com.weiver.applicant.repository;

import com.weiver.applicant.domain.ApplicantAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicantAgreementRepository extends JpaRepository<ApplicantAgreement, Long> {
}
