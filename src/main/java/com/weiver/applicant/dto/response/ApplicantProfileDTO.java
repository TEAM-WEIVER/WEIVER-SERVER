package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.Applicant;

public record ApplicantProfileDTO(
        Applicant applicant,
        String position
) {}
