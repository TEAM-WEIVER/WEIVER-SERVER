package com.weiver.jobposting.dto.response;

import com.weiver.jobposting.domain.EmailTemplate;
import com.weiver.jobposting.domain.JobPosting;

import java.time.LocalDate;
import java.util.List;

public record JobPostingResponseDTO(
        Long jdId,
        String title,
        LocalDate deadline,
        String jobCategory,
        String detailedJob,
        String jobDescription,
        String qualifications,
        String requirements,
        String preferredQualifications,

        List<String> competencyPriorities,
        List<String> requiredTechs,

        List<String> traitPriorities,

        String emailTitle,
        String emailContent
) {
    public static JobPostingResponseDTO of(JobPosting jobPosting, EmailTemplate emailTemplate) {
        return new JobPostingResponseDTO(
                jobPosting.getJdId(),
                jobPosting.getTitle(),
                jobPosting.getDeadline(),
                jobPosting.getJobCategory(),
                jobPosting.getDetailedJob(),
                jobPosting.getJobDescription(),
                jobPosting.getQualifications(),
                jobPosting.getRequirements(),
                jobPosting.getPreferredQualifications(),
                jobPosting.getCompetencyPriorities(),
                jobPosting.getRequiredTech(),
                jobPosting.getTraitPriorities(),
                emailTemplate.getEmailTitle(),
                emailTemplate.getEmailContent()
        );
    }
}
