package com.weiver.matching.dto.request;

import java.util.List;

/**
 * GET 요청의 쿼리 파라미터를 받을 DTO
 * */
public record ApplicantSearchCondition(
        Integer skillScoreMin,
        String cultureStyle,
        List<String> techStacks
) {
}
