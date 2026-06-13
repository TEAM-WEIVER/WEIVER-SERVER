package com.weiver.essay.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "자기소개서 등록 요청 DTO")
public record EssayAnswerRequestDTO(
        @Schema(
                description = "각 문항의 고유 ID(questionId)와 작성한 answer 목록",
                example = """
                        [
                          {"questionId": 1, "answer": "지원 동기입니다."},
                          {"questionId": 2, "answer": "직무 역량입니다."},
                          {"questionId": 3, "answer": "입사 후 포부입니다."}
                        ]
                        """
        )
        @Size(min = 3, max = 3)
        @NotEmpty
        List<@NotNull @Valid EssayAnswerItemDTO> answers
) {}
