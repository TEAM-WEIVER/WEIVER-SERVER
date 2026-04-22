package com.weiver.essay.dto.request;

import com.weiver.applicant.domain.Applicant;
import com.weiver.essay.domain.EssayAnswer;
import jakarta.validation.constraints.NotNull;

public record EssayAnswerRequestDTO (
        @NotNull String answer
){
    public EssayAnswer toEntity(Applicant applicant){
        return EssayAnswer.builder()
                .answer(this.answer)
                .applicant(applicant)
                .build();
    }
}
