package com.weiver.essay.dto.response;

import com.weiver.essay.domain.EssayAnswer;

public record EssayAnswerResponseDTO(
        long answerId,
        String answer
){
    public static EssayAnswerResponseDTO from(EssayAnswer essayAnswer){
        return new EssayAnswerResponseDTO(
                essayAnswer.getAnswerId(),
                essayAnswer.getAnswer()
        );
    }
}
