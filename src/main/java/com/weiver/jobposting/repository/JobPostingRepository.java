package com.weiver.jobposting.repository;

import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.type.JobPostingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    Page<JobPosting> findByCompany_CompanyId(Long companyId, Pageable pageable);
    Page<JobPosting> findByCompany_CompanyIdAndStatus(Long companyId, JobPostingStatus status, Pageable pageable);
}
