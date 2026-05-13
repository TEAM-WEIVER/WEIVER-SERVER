package com.weiver.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "면접 스크립트 질문/답변 DTO")
public record InterviewScriptDTO(
        @Schema(description = "면접 질문", example = "Spring Boot에서 트랜잭션 전파 옵션을 사용해 본 경험을 설명해 주세요.")
        String question,

        @Schema(description = "면접 답변 또는 AI가 생성한 답변 가이드", example = "프로젝트에서 주문 생성과 결제 승인 로직을 분리하면서 REQUIRED와 REQUIRES_NEW 전파 옵션의 차이를 경험했습니다.")
        String answer
) {}
