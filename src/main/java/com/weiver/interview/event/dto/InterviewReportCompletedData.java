package com.weiver.interview.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InterviewReportCompletedData(
        @JsonProperty("applicant_id")
        Long applicantId,

        @JsonProperty("interview_session_id")
        UUID interviewSessionId,

        @JsonProperty("applicant_name")
        String applicantName,

        @JsonProperty("skill_tags")
        List<String> skillTags,

        @JsonProperty("user_provided_tags")
        List<String> userProvidedTags,

        Map<String, Object> evaluation
) {
}
