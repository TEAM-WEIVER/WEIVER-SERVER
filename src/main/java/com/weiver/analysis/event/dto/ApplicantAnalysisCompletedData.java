package com.weiver.analysis.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ApplicantAnalysisCompletedData(
        @JsonProperty("applicant_id")
        Long applicantId,
        @JsonProperty("skill_tags")
        List<String> skillTags,
        String job,
        String role,
        @JsonProperty("culturefit_style")
        String culturefitStyle,
        @JsonProperty("culturefit_tags")
        List<String> culturefitTags
) {
}
