package com.weiver.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "AI 면접 시작 응답")
public record InterviewStartResponse(
        @JsonProperty("interview_session_id")
        @Schema(description = "면접 실행 단위 UUID")
        UUID interviewSessionId,

        @Schema(description = "현재 면접 세션 상태", example = "WAITING_FOR_QUESTION")
        String status
) {
}
