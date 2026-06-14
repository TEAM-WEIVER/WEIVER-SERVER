package com.weiver.essay.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "자기소개서 전체 수정 요청 DTO")
public record EssayAnswerUpdateRequestDTO(
        @Schema(
                description = "수정할 문항별 답변 목록. 전체 덮어쓰기 방식이므로 화면에 존재하는 모든 답변을 전송해야 합니다.",
                example = """
                        [
                          {"answerId": 1, "answer": "수정된 지원 동기입니다."},
                          {"answerId": 2, "answer": "수정된 직무 역량입니다."},
                          {"answerId": 3, "answer": "수정된 입사 후 포부입니다."}
                        ]
                        """
        )
        @NotEmpty
        @Size(min = 3, max = 3, message = "자기소개서 3개 문항의 전체 답변을 모두 전송해야 합니다.")
        List<@NotNull @Valid EssayAnswerUpdateItemDTO> answers
) {}
