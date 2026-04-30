package com.weiver.essay.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "자기소개서 수정 요청 DTO")
public record EssayAnswerUpdateRequestDTO(
        @Schema(description = "자기소개 답변", example = "안녕하세요. 저는 자라나는 미래의 꿈나무 이현우..")
        @NotBlank String answer
){}
