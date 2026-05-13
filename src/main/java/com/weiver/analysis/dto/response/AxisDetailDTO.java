package com.weiver.analysis.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "컬처핏 문화 축 상세 DTO")
public record AxisDetailDTO(
        @Schema(description = "문화 성향 축 이름입니다. 자율·혁신, 성과·영향, 안정·질서, 관계·공동체 중 하나입니다.", example = "자율·혁신")
        String name,

        @Schema(description = "해당 문화 성향 축의 총점입니다. 0부터 100까지의 퍼센트 정수입니다.", example = "82", minimum = "0", maximum = "100")
        int percentage,

        @Schema(description = "해당 문화 축을 구성하는 하위 성향 점수 목록입니다.")
        List<SubTraitDTO> subTraits
) {}
