package com.weiver.interview.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 면접 시작 요청")
public record InterviewStartRequest(
        @JsonProperty("interview_type")
        @Schema(description = "면접 유형. 현재 MVP에서는 저장하지 않고 세션 UUID로 면접 실행 단위를 구분합니다.", example = "TECHNICAL")
        String interviewType
) {
}
