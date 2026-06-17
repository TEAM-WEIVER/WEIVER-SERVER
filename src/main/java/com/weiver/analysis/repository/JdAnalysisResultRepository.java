package com.weiver.analysis.repository;

import com.weiver.analysis.domain.JdAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JdAnalysisResultRepository extends JpaRepository<JdAnalysisResult, Long> {
    Optional<JdAnalysisResult> findByJobPosting_JdId(Long jdId);
}
