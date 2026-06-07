package com.weiver.essay.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.essay.domain.EssayAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EssayAnswerRepository extends JpaRepository<EssayAnswer, Long> {
    Optional<EssayAnswer> findByApplicant(Applicant applicant);
    boolean existsByApplicant(Applicant applicant);
}
