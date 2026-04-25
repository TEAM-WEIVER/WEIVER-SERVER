package com.weiver.essay.dto.request;

import com.weiver.applicant.domain.Applicant;
import com.weiver.essay.domain.EssayAnswer;
import jakarta.validation.constraints.NotBlank;

public record EssayAnswerRequestDTO (
        @NotBlank String answer
){
    public EssayAnswer toEntity(Applicant applicant){
        return EssayAnswer.builder()
                .answer(this.answer)
                .applicant(applicant)
                .build();
    }
}
