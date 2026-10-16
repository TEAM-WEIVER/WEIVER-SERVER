package com.weiver.applicant.dto.response;

import com.weiver.applicant.type.ProfileSyncStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지원자 프로필 제출 여부 및 서류 작성 상태 응답 DTO")
public record ApplicantSubmissionStatusResponseDTO(

        @Schema(description = "프로필 제출 여부", example = "true")
        boolean submitted,

        @Schema(
                description = "프로필 동기화 상태. PENDING(미제출), REQUESTED(제출 후 AI 동기화 진행 중), "
                        + "COMPLETED(동기화 완료), FAILED(동기화 실패 - 재제출 가능). "
                        + "제출 버튼은 REQUESTED·COMPLETED에서 비활성화한다.",
                example = "COMPLETED"
        )
        ProfileSyncStatus syncStatus,

        @Schema(description = "프로필 제출(재제출) 가능 여부. 서류가 모두 작성되고 아직 제출되지 않았거나 동기화가 실패한 경우 true", example = "false")
        boolean submittable,

        @Schema(description = "이력서 작성 완료 여부", example = "true")
        boolean resumeCompleted,

        @Schema(description = "자기소개서 작성 완료 여부", example = "true")
        boolean essayCompleted,

        @Schema(description = "포트폴리오 작성 완료 여부", example = "true")
        boolean portfolioCompleted,

        @Schema(
                description = "AI 프로필 분석 완료 여부. 기술 리포트에 직무(job)와 역할(role)이 모두 저장됐으면 true. "
                        + "syncStatus=COMPLETED는 프로필 동기화 완료일 뿐 분석 완료가 아니다. "
                        + "면접 시작 버튼은 이 값이 true일 때만 활성화하고, false면 다시 조회해 분석 완료를 기다린다.",
                example = "true"
        )
        boolean analysisCompleted
) {
}
