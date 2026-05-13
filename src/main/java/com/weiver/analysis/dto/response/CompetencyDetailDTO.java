package com.weiver.analysis.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 역량 분석 상세 점수 DTO")
public record CompetencyDetailDTO(
        @Schema(description = "역량 이름입니다. 예: 성장가능성, 대처능력, 일관성, 협업 및 커뮤니케이션, 문제해결력, 논리력", example = "문제해결력")
        String name,

        @Schema(description = "해당 역량의 평가 점수입니다. 5점 척도 원본 점수를 0부터 100까지의 퍼센트로 환산한 값입니다.", example = "88", minimum = "0", maximum = "100")
        Integer percentage
) {}
