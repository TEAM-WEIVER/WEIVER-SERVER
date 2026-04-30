package com.weiver.applicant.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.WorkExperience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkExperienceRepository extends JpaRepository<WorkExperience, Long> {
    List<WorkExperience> findAllByApplicant(Applicant applicant);
}
