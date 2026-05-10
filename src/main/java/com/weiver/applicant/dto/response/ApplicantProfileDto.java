package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.Applicant;

public record ApplicantProfileDto(
        Applicant applicant,
        String position
) {}