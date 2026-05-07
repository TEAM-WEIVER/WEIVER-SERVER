package com.weiver.matching.dto.response;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.matching.domain.MatchResult;

import java.util.List;

public record ApplicantListResponseDTO(
        String publicId,
        String profileImageUrl,
        String applicantName,
        String position,

        // MatchResult
        Float skillScore,       // 스킬핏 점수

        // 구직자 컬처 리포트
        CulturefitStyle cultureStyle,      // 컬처핏 스타일
        List<String> cultureTags, // 컬처핏 태그

        // 구직자 기술 리포트
        List<String> techStacks   // 스킬 태그
) {
    public static ApplicantListResponseDTO of(MatchResult matchResult, CultureReport cultureReport,
                                              TechnicalSkillReport technicalSkillReport, String position) {
        return new ApplicantListResponseDTO(
                matchResult.getApplicant().getPublicId(),
                matchResult.getApplicant().getPhotoUrl(),
                matchResult.getApplicant().getName(),
                position,
                matchResult.getSkillScore(),
                cultureReport.getCulturefitStyles(),
                cultureReport.getCulturefitTag(), // Hibernate가 파싱 완료한 List 그대로 사용!
                technicalSkillReport.getSkillTags()   // Hibernate가 파싱 완료한 List 그대로 사용!
        );
    }
}
