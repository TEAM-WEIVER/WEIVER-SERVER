package com.weiver.matching.dto.request;

import java.util.List;

/**
 * GET 요청의 쿼리 파라미터를 받을 DTO
 * */
public record ApplicantSearchCondition(
        Long jdId,                  // 특정 공고 ID
        String keyword,             // 지원자 이름 검색
        Integer skillScoreMin,      // 최소 스킬핏 점수
        String cultureStyle,        // 컬처핏 스타일
        List<String> techStacks     // 기술 스택 필터링
) {
}
