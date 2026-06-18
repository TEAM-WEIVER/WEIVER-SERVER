package com.weiver.interview.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "AI 면접 답변 제출 요청")
public record InterviewAnswerSubmitRequest(
        @NotBlank(message = "question_code는 필수입니다.")
        @JsonProperty("question_code")
        @Schema(description = "답변 대상 질문 코드", example = "S_01_00")
        String questionCode,

        @NotNull(message = "sequence는 필수입니다.")
        @Schema(description = "답변 대상 질문 순서", example = "1")
        Integer sequence,

        @NotBlank(message = "answer는 필수입니다.")
        @Schema(description = "지원자 답변", example = "프로젝트에서 Spring Boot와 JPA를 활용해 주문 도메인을 구현했습니다.")
        String answer
) {
}
