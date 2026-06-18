package com.weiver.interview.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record InterviewTranscriptSaveRequestedData(
        @JsonProperty("applicant_id")
        Long applicantId,

        @JsonProperty("interview_session_id")
        UUID interviewSessionId,

        @JsonProperty("skill_interview")
        TranscriptSectionData skillInterview,

        @JsonProperty("culture_interview")
        TranscriptSectionData cultureInterview
) {
    public record TranscriptSectionData(
            List<TranscriptTurnData> turns
    ) {
    }

    public record TranscriptTurnData(
            String question,
            String answer
    ) {
    }
}
