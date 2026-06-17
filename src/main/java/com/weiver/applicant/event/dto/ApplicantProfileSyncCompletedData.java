package com.weiver.applicant.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApplicantProfileSyncCompletedData(
        @JsonProperty("applicant_id")
        Long applicantId,
        Boolean synced
) {
}
