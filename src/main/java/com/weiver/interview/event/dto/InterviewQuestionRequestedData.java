package com.weiver.interview.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record InterviewQuestionRequestedData(
        @JsonProperty("applicant_id")
        Long applicantId,

        @JsonProperty("applicant_name")
        String applicantName,

        @JsonProperty("interview_session_id")
        UUID interviewSessionId,

        @JsonProperty("last_question_code")
        String lastQuestionCode,

        Integer sequence,
        String job,
        String role,

        @JsonProperty("last_interview")
        LastInterviewData lastInterview
) {
    public record LastInterviewData(
            String question,
            String answer
    ) {
    }
}
