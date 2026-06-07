package com.weiver.applicant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지원자 필수 제출 서류 작성 상태 응답 DTO")
public record ApplicantDocumentStatusResponseDTO(

        @Schema(description = "이력서 작성 완료 여부", example = "true")
        boolean resumeCompleted,

        @Schema(description = "자기소개서 작성 완료 여부", example = "true")
        boolean essayCompleted,

        @Schema(description = "포트폴리오 작성 완료 여부", example = "true")
        boolean portfolioCompleted
) {
}
