package com.weiver.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "지원자 AI 요약 카드 응답 DTO")
public record SummaryCardResponseDTO(
        @Schema(description = "채용 공고와 지원자 정보를 바탕으로 AI가 생성한 종합 평가 요약 문장입니다.", example = "해당 지원자는 백엔드 API 개발 경험과 협업 경험이 공고 요구사항과 잘 맞으며, 문제 해결 역량이 강점으로 확인됩니다.")
        String aiSummary,

        @Schema(description = "지원자의 주요 경력 목록입니다. 지원자 상세 화면의 핵심 경력 요약 영역에 사용됩니다.")
        @JsonProperty("majorCareers")
        List<MajorCareerDTO> majorCareerDTO
) {
    public static SummaryCardResponseDTO of(String aiSummary, List<MajorCareerDTO> majorCareerDTO) {
        return new SummaryCardResponseDTO(aiSummary, majorCareerDTO);
    }
}
