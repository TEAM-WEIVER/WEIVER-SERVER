package com.weiver.jobposting.dto.response;

import com.weiver.jobposting.domain.JobPosting;

public record JobPostingsDetails(
        Long jdId,
        String title,
        String status,
        String jobCategory,
        String detailedJob,
        Long newApplicantCount
) {
    public static JobPostingsDetails of(JobPosting jobPosting, long newApplicantCount){
        return new JobPostingsDetails(
                jobPosting.getJdId(),
                jobPosting.getTitle(),
                jobPosting.getStatus().getDescription(),
                jobPosting.getJobCategory(),
                jobPosting.getDetailedJob(),
                newApplicantCount
        );
    }
}
