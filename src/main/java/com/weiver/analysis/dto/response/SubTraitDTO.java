package com.weiver.analysis.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "컬처핏 하위 성향 점수 DTO")
public record SubTraitDTO(
        @Schema(description = "하위 성향 이름입니다. 예: 자기방향, 자극, 성취, 권력, 안전, 순응, 전통, 선의, 보편주의", example = "자기방향")
        String name,

        @Schema(description = "해당 하위 성향의 점수입니다. 0부터 100까지의 퍼센트 정수입니다.", example = "76", minimum = "0", maximum = "100")
        int percentage
) {}
