package com.weiver.jobposting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "페이징 처리 메타 정보")
public record PageInfo(
        @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
        int pageNumber,

        @Schema(description = "페이지당 데이터 개수", example = "3")
        int pageSize,

        @Schema(description = "전체 데이터 개수", example = "25")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "9")
        int totalPages,

        @Schema(description = "마지막 페이지 여부 (true면 더 이상 불러올 데이터 없음)", example = "false")
        boolean isLast
) {}
