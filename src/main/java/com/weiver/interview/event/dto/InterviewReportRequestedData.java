package com.weiver.interview.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record InterviewReportRequestedData(
        @JsonProperty("applicant_id")
        Long applicantId,

        @JsonProperty("interview_session_id")
        UUID interviewSessionId
) {
}
