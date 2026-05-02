package com.weiver.jobposting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record JobPostingUpdateDTO(
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
        @NotBlank String emailContent,
        Boolean isEmailBannerDeleted
) {
}
