package com.weiver.applicant.repository;

import com.weiver.applicant.domain.Applicant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {
    Optional<Applicant> findByApplicantId(long applicantId);
}
