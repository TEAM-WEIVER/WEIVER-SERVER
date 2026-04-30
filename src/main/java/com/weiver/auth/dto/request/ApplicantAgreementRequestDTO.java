package com.weiver.auth.dto.request;

import jakarta.validation.constraints.NotNull;

public record ApplicantAgreementRequestDTO(
        @NotNull(message = "서비스 이용 약관 동의 여부는 필수입니다.")
        Boolean termsOfService,

        @NotNull(message = "개인정보 처리방침 동의 여부는 필수입니다.")
        Boolean privacyPolicy,

        @NotNull(message = "개인회원 이용약관 동의 여부는 필수입니다.")
        Boolean individualMemberTerms,

        @NotNull(message = "AI 분석 서비스 이용동의 여부는 필수입니다.")
        Boolean aiAnalysisConsent,

        @NotNull(message = "민감정보 및 영상/음성 데이터 이용 동의 여부는 필수입니다.")
        Boolean sensitiveDataConsent,

        @NotNull(message = "마케팅 정보 수신 동의 여부를 선택해주세요.")
        Boolean marketingConsent
) {
}
