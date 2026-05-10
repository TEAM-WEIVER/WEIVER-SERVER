package com.weiver.analysis.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "컬처핏 상위 컬처핏 축 DTO")
public record CultureAxisDTO(
        @Schema(description = "문화 성향 축 이름입니다.", example = "자율·혁신")
        String name,

        @Schema(description = "해당 문화 성향 축의 점수입니다. 0부터 100까지의 퍼센트 정수입니다.", example = "82", minimum = "0", maximum = "100")
        int percentage
) {}
