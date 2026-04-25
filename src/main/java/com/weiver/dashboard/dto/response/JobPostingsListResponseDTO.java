package com.weiver.dashboard.dto.response;

import com.weiver.jobposting.domain.JobPosting;

public record JobPostingsListResponseDTO(
        Long jdId,
        String title,
        String status,
        String jobCategory,
        String detailedJob,
        Long newApplicantCount
) {
    public static JobPostingsListResponseDTO of(JobPosting jobPosting, long newApplicantCount){
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
