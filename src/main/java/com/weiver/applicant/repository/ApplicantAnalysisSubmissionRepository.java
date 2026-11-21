package com.weiver.applicant.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAnalysisSubmission;
import com.weiver.applicant.type.AnalysisSubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface ApplicantAnalysisSubmissionRepository extends JpaRepository<ApplicantAnalysisSubmission, Long> {

    Optional<ApplicantAnalysisSubmission> findByApplicantAndSubmissionVersion(Applicant applicant, Integer submissionVersion);

    /** 대기 지표용: 해당 상태인 제출 건 수. */
    long countByStatus(AnalysisSubmissionStatus status);

    /** 대기 지표용: 해당 상태인 제출 건 중 가장 이른 요청 시각. */
    @Query("SELECT MIN(s.requestedAt) FROM ApplicantAnalysisSubmission s WHERE s.status = :status")
    Optional<OffsetDateTime> findOldestRequestedAt(@Param("status") AnalysisSubmissionStatus status);
}
