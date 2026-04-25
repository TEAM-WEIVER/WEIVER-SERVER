package com.weiver.matching.dto.response;

import java.util.List;

public record ApplicantListResponseDTO(
        Long applicantId,
        String profileImageUrl,
        String applicantName,
        String careerLevel,

        // MatchResult
        Integer skillScore,       // 스킬핏 점수

        // 구직자 컬처 리포트
        String cultureStyle,      // 컬처핏 스타일
        List<String> cultureTags, // 컬처핏 태그

        // 구직자 기술 리포트
        List<String> techStacks   // 스킬 태그
) {}
