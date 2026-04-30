package com.weiver.applicant.repository;

import com.weiver.applicant.domain.Applicant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicantRepository extends JpaRepository<Applicant, Long> {
    boolean existsByEmail(String email);
    Optional<Applicant> findByEmailAndDeletedFalse(String email);
    Optional<Applicant> findByApplicantIdAndDeletedFalse(Long applicantId);
}
