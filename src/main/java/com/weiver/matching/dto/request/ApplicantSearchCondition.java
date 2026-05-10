package com.weiver.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;

import java.util.List;

/**
 * GET 요청의 쿼리 파라미터를 받을 DTO
 * */
@Builder
@Tag(name = "지원자 검색 조건 DTO", description = "공고에 매칭된 지원자들을 검색하기 위한 조건을 담는 DTO")
public record ApplicantSearchCondition(
        @Schema(description = "채용 공고 고유 ID", example = "1")
        Long jdId,

        @Schema(description = "지원자 이름 검색 키워드", example = "홍길동")
        String keyword,

        @Schema(description = "스킬핏 점수 최소값 (ex. 80 이상)", example = "80")
        Integer skillScoreMin,

        @Schema(description = "컬처핏 스타일", example = "추진형 실행가",
        allowableValues = {"AGGRESSIVE_INNOVATOR", "INCLUSIVE_INNOVATOR", "STRATEGIC_GUARDIAN", "STEADY_SUPPORTER"})
        String cultureStyle,

        @Schema(description = "기술 스택 리스트", example = "[\"React\", \"Figma\"]")
        List<String> techStacks
) {
}
