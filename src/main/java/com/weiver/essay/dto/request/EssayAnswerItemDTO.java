package com.weiver.essay.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "자기소개서 문항별 답변 DTO")
public record EssayAnswerItemDTO(
        @Schema(description = "자기소개서 문항 고유 ID", example = "1")
        @NotNull
        Long questionId,

        @Schema(description = "해당 문항에 작성한 답변", example = "지원 동기입니다.")
        @NotBlank
        String answer
) {}
