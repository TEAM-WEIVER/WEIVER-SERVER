package com.weiver.matching.dto.response;

import com.weiver.applicant.domain.Applicant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지원자 카드 프로필 상세 DTO")
public record ProfileDetailDTO(
        @Schema(description = "지원자 내부 고유 ID", example = "15")
        Long applicantId,

        @Schema(description = "지원자 실명", example = "홍길동")
        String name,

        @Schema(description = "지원자 휴대폰 번호", example = "010-1234-5678")
        String phoneNumber,

        @Schema(description = "지원자 이메일 주소", example = "applicant@example.com")
        String email,

        @Schema(description = "지원자 프로필 이미지 URL입니다. 등록된 이미지가 없으면 null입니다.", example = "https://weiver-public-bucket.s3.ap-northeast-2.amazonaws.com/profiles/applicant.jpg", nullable = true)
        String photoUrl,

        @Schema(description = "지원자의 대표 직급 또는 포지션입니다. 경력 정보에서 추출되며 값이 없으면 null입니다.", example = "백엔드 개발자", nullable = true)
        String position
) {
    public static ProfileDetailDTO of(Applicant applicant, String position) {
        return new ProfileDetailDTO(
                applicant.getApplicantId(),
                applicant.getName(),
                applicant.getPhoneNumber(),
                applicant.getEmail(),
                applicant.getPhotoUrl(),
                position
        );
    }
}
