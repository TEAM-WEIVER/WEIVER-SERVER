package com.weiver.jobposting.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record JdAnalysisCompletedData(
        @JsonProperty("jd_id")
        Long jdId,
        @JsonProperty("company_id")
        Long companyId,
        @JsonProperty("original_text")
        String originalText,
        List<Double> embedding
) {
}
