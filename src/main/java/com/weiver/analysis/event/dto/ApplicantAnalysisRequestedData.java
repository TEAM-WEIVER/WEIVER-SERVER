package com.weiver.analysis.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApplicantAnalysisRequestedData(
        @JsonProperty("applicant_id")
        Long applicantId
) {
}
