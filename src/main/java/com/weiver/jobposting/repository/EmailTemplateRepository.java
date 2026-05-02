package com.weiver.jobposting.repository;

import com.weiver.jobposting.domain.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {
    @Query("SELECT e FROM EmailTemplate e JOIN FETCH e.jobPosting WHERE e.jobPosting.jdId = :jdId")
    Optional<EmailTemplate> findWithJobPostingByJdId(@Param("jdId") Long jdId);
}
