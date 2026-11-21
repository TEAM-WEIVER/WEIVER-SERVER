package com.weiver.applicant.dto.response;

import com.weiver.applicant.type.ProfileSyncStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지원자 프로필 제출 여부 및 서류 작성 상태 응답 DTO")
public record ApplicantSubmissionStatusResponseDTO(

        @Schema(description = "프로필 제출 여부. 현재 제출 건이 있으면(REQUESTED·COMPLETED·FAILED) true", example = "true")
        boolean submitted,

        @Schema(
                description = "현재 제출 건 상태. PENDING(미제출), REQUESTED(제출 후 AI 분석 진행 중), "
                        + "COMPLETED(현재 제출의 분석 결과 저장 완료), FAILED(분석 실패 - 같은 내용으로 재제출 가능). "
                        + "제출 버튼은 REQUESTED·COMPLETED에서 비활성화한다.",
                example = "COMPLETED"
        )
        ProfileSyncStatus syncStatus,

        @Schema(description = "프로필 제출(재제출) 가능 여부. 서류가 모두 작성되고, 아직 제출하지 않았거나 현재 제출 건의 분석이 실패(FAILED)한 경우 true", example = "false")
        boolean submittable,

        @Schema(description = "이력서 작성 완료 여부", example = "true")
        boolean resumeCompleted,

        @Schema(description = "자기소개서 작성 완료 여부", example = "true")
        boolean essayCompleted,

        @Schema(description = "포트폴리오 작성 완료 여부", example = "true")
        boolean portfolioCompleted,

        @Schema(
                description = "AI 프로필 분석 완료 여부. 현재 제출 건 상태가 COMPLETED이면 true(syncStatus=COMPLETED와 같은 기준). "
                        + "이전 제출의 리포트가 남아 있어도 현재 제출이 REQUESTED·FAILED면 false다. "
                        + "면접 시작 버튼은 이 값이 true일 때만 활성화하고, REQUESTED면 다시 조회해 분석 완료를 기다린다.",
                example = "true"
        )
        boolean analysisCompleted
) {
}
