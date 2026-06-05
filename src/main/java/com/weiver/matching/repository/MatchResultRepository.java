package com.weiver.matching.repository;

import com.weiver.matching.domain.MatchResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long>, MatchResultRepositoryCustom {
    @EntityGraph(attributePaths = {"jobPosting", "jobPosting.company"})
    List<MatchResult> findAllByIsNotifiedFalse();

    Optional<MatchResult> findByJobPosting_JdIdAndApplicant_PublicId(Long jdId, String applicantPublicId);

    Optional<MatchResult> findByJobPosting_JdIdAndApplicant_ApplicantId(Long jdId, Long applicantId);

    void deleteByJobPosting_JdId(Long jdId);

    boolean existsByJobPosting_JdIdAndApplicant_PublicIdAndJobPosting_Company_PublicId(
            Long jdId, String applicantPublicId, String companyPublicId
    );
}
