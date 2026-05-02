package com.weiver.jobposting.dto.request;

import com.weiver.company.domain.Company;
import com.weiver.jobposting.domain.EmailTemplate;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.type.JobPostingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record JobPostingRequestDTO(
        @NotBlank String title,
        @NotNull LocalDate deadline,
        @NotBlank String jobCategory,
        @NotBlank String detailedJob,
        String jobDescription,
        String qualifications,
        String requirements,
        String preferredQualifications,

        List<String> competencyPriorities,
        List<String> requiredTechs,

        List<String> traitPriorities,

        @NotBlank String emailTitle,
        @NotBlank String emailContent

) {
    public JobPosting toJobPosting(Company company){
        return JobPosting.builder()
                .title(this.title)
                .jobCategory(this.jobCategory)
                .deadline(this.deadline)
                .status(JobPostingStatus.ACTIVE)
                .jobDescription(this.jobDescription)
                .qualifications(this.qualifications)
                .requirements(this.requirements)
                .preferredQualifications(this.preferredQualifications)
                .competencyPriorities(this.competencyPriorities)
                .requiredTech(this.requiredTechs)
                .traitPriorities(this.traitPriorities)
                .build();
    }

    public EmailTemplate toEmailTemplate(JobPosting jobPosting, String emailBannerUrl){
        return EmailTemplate.builder()
                .emailTitle(this.emailTitle)
                .emailContent(this.emailContent)
                .emailBannerUrl(emailBannerUrl)
                .jobPosting(jobPosting)
                .build();
    }
}
