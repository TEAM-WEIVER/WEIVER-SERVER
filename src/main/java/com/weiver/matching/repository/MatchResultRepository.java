package com.weiver.matching.repository;

import com.weiver.matching.domain.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long>, MatchResultRepositoryCustom {
    boolean existsByJobPosting_JdIdAndApplicant_PublicIdAndJobPosting_Company_PublicId(
            Long jdId, String applicantPublicId, String companyPublicId
    );
}
