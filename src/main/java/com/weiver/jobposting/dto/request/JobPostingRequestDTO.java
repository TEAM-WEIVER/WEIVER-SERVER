package com.weiver.jobposting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

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
        String preferredQualifications,

        List<String> competencyPriorities,
        List<String> requiredTechs,

        List<String> traitPriorities,

        @NotBlank String emailTitle,
        @NotBlank String emailContent,
        String emailBannerUrl
) {}
