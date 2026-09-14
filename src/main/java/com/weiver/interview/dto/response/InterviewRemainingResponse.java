package com.weiver.interview.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "AI 면접 잔여 횟수 · 재지원 D-day 조회 응답")
public record InterviewRemainingResponse(
        @Schema(description = "면접 총 가능 횟수(1개월당 1회)", example = "1")
        int totalCount,

        @Schema(description = "잔여 면접 가능 횟수(0 또는 1)", example = "1")
        int remainingCount,

        @Schema(description = "재지원까지 남은 일수(지금 가능하면 0)", example = "0")
        long reapplyDDay,

        @Schema(description = "재지원 가능 날짜(지금 가능하면 null)", example = "2026-10-13")
        LocalDate reapplyAvailableDate,

        @Schema(
                description = "분석(면접 결과)을 아직 제출하지 않은 종료된 면접 세션 ID. "
                        + "값이 있으면 해당 세션으로 `POST /api/interviews/{id}/analysis`를 호출할 수 있고, "
                        + "없으면(null) 제출 버튼을 비활성화한다.",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        )
        UUID pendingSubmissionSessionId
) {

    /** 1개월당 허용되는 면접 총 횟수. */
    private static final int TOTAL_COUNT = 1;

    /**
     * 면접(재지원) 가능 상태 응답.
     *
     * <p>면접 결과를 한 번도 제출하지 않았거나 제출 후 1개월 쿨다운이 끝나 지금 바로 면접이 가능한 경우.
     * total=1, remaining=1, D-day=0, 재지원 날짜 없음(null).
     */
    public static InterviewRemainingResponse available(UUID pendingSubmissionSessionId) {
        return new InterviewRemainingResponse(TOTAL_COUNT, TOTAL_COUNT, 0, null, pendingSubmissionSessionId);
    }

    /**
     * 재지원 대기 상태 응답.
     *
     * <p>면접 결과를 제출해 1개월 쿨다운이 진행 중인 경우.
     * total=1, remaining=0, 재응시 가능일까지 남은 일수(D-day)와 날짜를 함께 담는다.
     */
    public static InterviewRemainingResponse waiting(long reapplyDDay, LocalDate reapplyAvailableDate) {
        return new InterviewRemainingResponse(TOTAL_COUNT, 0, reapplyDDay, reapplyAvailableDate, null);
    }
}
