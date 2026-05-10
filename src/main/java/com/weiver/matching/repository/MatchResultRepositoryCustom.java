package com.weiver.matching.repository;

import com.querydsl.core.Tuple;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.dto.request.ApplicantSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface MatchResultRepositoryCustom {
    Page<Tuple> searchApplicantsTuple(ApplicantSearchCondition condition, Pageable pageable);
    Optional<MatchResult> findMatchResultForContact(Long jdId, String applicantId, String companyId);
}
