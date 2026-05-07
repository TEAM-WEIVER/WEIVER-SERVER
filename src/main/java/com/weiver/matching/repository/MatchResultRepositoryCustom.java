package com.weiver.matching.repository;

import com.querydsl.core.Tuple;
import com.weiver.matching.dto.request.ApplicantSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MatchResultRepositoryCustom {
    Page<Tuple> searchApplicantsTuple(ApplicantSearchCondition condition, Pageable pageable);
}
