package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.Applicant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "구직자 기본 정보 응답 DTO")
public record ApplicantDetailResponseDTO(

        @Schema(description = "프로필 사진 S3 URL (사진이 없을 경우 null 반환)", example = "https://weiver-public-bucket.s3.ap-northeast-2.amazonaws.com/profiles/uuid.jpg", nullable = true)
        String photoUrl,

        @Schema(description = "지원자 실명", example = "이현우")
        String name,

        @Schema(description = "생년월일 (YYYY-MM-DD 형식)", example = "2000-01-01")
        String birthday,

        @Schema(description = "휴대폰 번호 (하이픈 포함)", example = "010-1234-5678")
        String phoneNumber,

        @Schema(description = "연락 가능한 이메일 주소", example = "weiver@example.com")
        String email
) {
    public static ApplicantDetailResponseDTO from(Applicant applicant){
        return new ApplicantDetailResponseDTO(
                applicant.getPhotoUrl(),
                applicant.getName(),
                applicant.getBirthday().toString(),
                applicant.getPhoneNumber(),
                applicant.getEmail()
        );
    }
}