package com.weiver.essay.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "자기소개서 문항별 수정 요청 DTO")
public record EssayAnswerUpdateItemDTO(
        @Schema(description = "답변 고유 ID", example = "1")
        @NotNull
        Long answerId,

        @Schema(description = "수정할 답변 내용", example = "수정된 지원 동기입니다.")
        @NotBlank
        String answer
) {}
