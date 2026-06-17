package com.weiver.matching.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MatchingRequestedData(
        @JsonProperty("company_id")
        Long companyId,
        @JsonProperty("jd_id")
        Long jdId,
        @JsonProperty("required_skills")
        List<String> requiredSkills,
        String requirements
) {
}
