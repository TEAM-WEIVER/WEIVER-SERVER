package com.weiver.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ApplicantAgreementRequestDTO(
        @Schema(description = "서비스 이용 약관 동의 여부", example = "true")
        @NotNull(message = "서비스 이용 약관 동의 여부는 필수입니다.")
        Boolean termsOfService,

        @Schema(description = "개인정보 처리방침 동의 여부", example = "true")
        @NotNull(message = "개인정보 처리방침 동의 여부는 필수입니다.")
        Boolean privacyPolicy,

        @Schema(description = "개인회원 이용약관 동의 여부", example = "true")
        @NotNull(message = "개인회원 이용약관 동의 여부는 필수입니다.")
        Boolean individualMemberTerms,

        @Schema(description = "AI 분석 서비스 이용 동의 여부", example = "true")
        @NotNull(message = "AI 분석 서비스 이용동의 여부는 필수입니다.")
        Boolean aiAnalysisConsent,

        @Schema(description = "민감정보 및 영상/음성 데이터 이용 동의 여부", example = "true")
        @NotNull(message = "민감정보 및 영상/음성 데이터 이용 동의 여부는 필수입니다.")
        Boolean sensitiveDataConsent,

        @Schema(description = "마케팅 정보 수신 동의 여부", example = "false")
        @NotNull(message = "마케팅 정보 수신 동의 여부를 선택해주세요.")
        Boolean marketingConsent
) {
}
