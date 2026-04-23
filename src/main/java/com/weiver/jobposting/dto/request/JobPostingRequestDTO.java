package com.weiver.jobposting.dto.request;

import com.weiver.company.domain.Company;
import com.weiver.jobposting.domain.JobPosting;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record JobPostingRequestDTO(
        @NotBlank String title,
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "마감 기한은 YYYY-MM-DD 형식이어야 합니다.")
        String deadline,
        @NotBlank String jobCategory,
        @NotBlank String detailedJob,
        String jobDescription,
        String qualifications,
        String requirements,
        String preferredQualifications
) {
    public JobPosting toEntity(Company company){
        return JobPosting.builder()
                .title(this.title)
                .deadline(LocalDate.parse(this.deadline))
                .jobCategory(this.jobCategory)
                .detailedJob(this.detailedJob)
                .jobDescription(this.jobDescription)
                .qualifications(this.qualifications)
                .requirements(this.requirements)
                .preferredQualifications(this.preferredQualifications)
                .company(company)
                .build();
    }
}
