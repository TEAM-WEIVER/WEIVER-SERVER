package com.weiver.jobposting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;

public record JobPostingRequestDTO(
        @NotBlank String title,
        @NotBlank LocalDate deadline,
        @NotBlank String jobCategory,
        @NotBlank String detailedJob,
        String jobDescription,
        String qualifications,
        String requirements,
        String preferredQualifications,
        String status, // 공고 활성화 여부

        List<String> competencyPriorities,
        List<String> requiredTechs,

        List<String> traitPriorities,

        @NotBlank String emailTitle,
        @NotBlank String emailContent,
        String emailBannerUrl
) {}
