package com.weiver.portfolio.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.portfolio.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    Optional<Portfolio> findByApplicant(Applicant applicant);
    boolean existsByApplicant(Applicant applicant);
}
