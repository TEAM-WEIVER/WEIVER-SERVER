package com.weiver.matching.dto.response;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.matching.domain.MatchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "지원자 리스트 조회 응답 DTO", description = "공고에 매칭된 지원자들의 리스트를 반환하는 DTO")
public record ApplicantListResponseDTO(
        @Schema(description = "구직자 ID", example = "3333")
        String publicId,
        @Schema(description = "구직자 프로필 이미지 URL", example = "https://example.com/profile.jpg")
        String profileImageUrl,
        @Schema(description = "구직자 이름", example = "홍길동")
        String applicantName,
        @Schema(description = "직급", example = "신입")
        String position,

        @Schema(description = "스킬핏 점수", example = "85.5")
        Float skillScore,

        @Schema(description = "컬처핏 스타일", example = "추진형 실행가")
        CulturefitStyle cultureStyle,

        @Schema(description = "컬처핏 태그 리스트", example = "[\"팀워크\", \"리더십\"]")
        List<String> cultureTags,

        @Schema(description = "기술 스택 태그 리스트", example = "[\"React\", \"Java\"]")
        List<String> techStacks
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
