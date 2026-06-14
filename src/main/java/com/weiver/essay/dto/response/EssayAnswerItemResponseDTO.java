package com.weiver.essay.dto.response;

import com.weiver.essay.domain.EssayAnswer;
import com.weiver.essay.domain.EssayQuestion;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자기소개서 문항별 답변 응답 DTO")
public record EssayAnswerItemResponseDTO(
        @Schema(description = "답변 고유 ID", example = "1")
        Long answerId,

        @Schema(description = "자기소개서 문항 고유 ID", example = "1")
        Long questionId,

        @Schema(description = "문항 번호", example = "1")
        Integer sequence,

        @Schema(description = "질문 내용", example = "지원 동기는 무엇인가요?")
        String question,

        @Schema(description = "최대 글자 수", example = "500")
        Integer maxLength,

        @Schema(description = "사용자가 작성한 답변", example = "지원 동기입니다.")
        String answer
) {
    public static EssayAnswerItemResponseDTO from(EssayAnswer essayAnswer) {
        EssayQuestion essayQuestion = essayAnswer.getEssayQuestion();

        return new EssayAnswerItemResponseDTO(
                essayAnswer.getAnswerId(),
                essayQuestion.getQuestionId(),
                essayQuestion.getSequence(),
                essayQuestion.getQuestion(),
                essayQuestion.getMaxLength(),
                essayAnswer.getAnswer()
        );
    }
}
