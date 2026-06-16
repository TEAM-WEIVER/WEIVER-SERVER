package com.weiver.matching.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MatchingCompletedData(
        @JsonProperty("jd_id")
        Long jdId,
        List<MatchData> matches
) {
    public record MatchData(
            @JsonProperty("applicant_id")
            Long applicantId,
            @JsonProperty("skill_score")
            Float skillScore,
            @JsonProperty("culture_score")
            Float cultureScore,
            @JsonProperty("final_score")
            Float finalScore,
            String reason
    ) {
    }
}
