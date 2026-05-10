package com.weiver.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SummaryCardResponseDTO (
        String aiSummary,
        @JsonProperty("majorCareers")
        List<MajorCareerDTO> majorCareerDTO
){
    public static SummaryCardResponseDTO of(String aiSummary, List<MajorCareerDTO> majorCareerDTO){
        return new SummaryCardResponseDTO(aiSummary, majorCareerDTO);
    }
}
