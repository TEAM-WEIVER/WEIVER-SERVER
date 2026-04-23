package com.weiver.company.dto.response;

import com.weiver.jobposting.domain.JobPosting;

public record JobPostingsListResponseDTO(
        long jbId,
        String title,
        String status,
        String jobCategory,
        String detailedJob,
        long newApplicantCount
) {
    public static JobPostingsListResponseDTO from(JobPosting jobPosting, long newApplicantCount){
        return new JobPostingsListResponseDTO(
                jobPosting.getJdId(),
                jobPosting.getTitle(),
                jobPosting.getStatus().getDescription(),
                jobPosting.getJobCategory(),
                jobPosting.getDetailedJob(),
                newApplicantCount
        );
    }
}
