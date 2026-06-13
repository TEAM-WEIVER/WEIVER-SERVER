package com.weiver.essay.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "자기소개서 답변 목록 응답 DTO")
public record EssayAnswerResponseDTO(
        @Schema(description = "문항별 자기소개서 답변 목록")
        List<EssayAnswerItemResponseDTO> answers
) {}
