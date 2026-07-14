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
        String requirements,
        @JsonProperty("competency_priorities")
        List<PriorityData> competencyPriorities,
        @JsonProperty("trait_priorities")
        List<PriorityData> traitPriorities
) {
    public record PriorityData(
            int rank,
            String code,
            String name
    ) {
    }
}
