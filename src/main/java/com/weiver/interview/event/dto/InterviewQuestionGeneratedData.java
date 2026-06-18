package com.weiver.interview.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record InterviewQuestionGeneratedData(
        @JsonProperty("applicant_id")
        Long applicantId,

        @JsonProperty("interview_session_id")
        UUID interviewSessionId,

        @JsonProperty("next_question_code")
        String nextQuestionCode,

        Integer sequence,
        String question
) {
}
