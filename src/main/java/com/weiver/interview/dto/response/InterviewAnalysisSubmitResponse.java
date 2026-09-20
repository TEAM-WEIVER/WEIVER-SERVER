package com.weiver.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "면접 분석 요청(면접 결과 제출) 응답")
public record InterviewAnalysisSubmitResponse(

        @Schema(description = "분석을 요청한 면접 세션 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @JsonProperty("interview_session_id")
        UUID interviewSessionId,

        @Schema(description = "요청 직후 면접 세션 상태", example = "TRANSCRIPT_SAVE_REQUESTED")
        String status,

        @Schema(description = "다음 면접 응시 가능 시점(제출 시각 + 1개월)", example = "2026-10-13T14:30:00")
        @JsonProperty("next_available_interview_at")
        LocalDateTime nextAvailableInterviewAt
) {
}
