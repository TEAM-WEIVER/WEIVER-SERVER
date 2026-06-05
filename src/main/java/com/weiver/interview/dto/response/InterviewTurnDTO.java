package com.weiver.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "면접 질문/답변 DTO")
public record InterviewTurnDTO(
        @JsonProperty("question_code")
        @Schema(description = "면접 질문 코드", example = "S_01_00")
        String questionCode,

        @Schema(description = "면접 질문 생성 턴 순서", example = "1")
        Integer sequence,

        @Schema(description = "면접 질문", example = "Spring Boot에서 트랜잭션 전파 옵션을 사용해 본 경험을 설명해 주세요.")
        String question,

        @Schema(description = "면접 답변", example = "프로젝트에서 주문 생성과 결제 승인 로직을 분리하면서 REQUIRED와 REQUIRES_NEW 전파 옵션의 차이를 경험했습니다.")
        String answer
) {}
