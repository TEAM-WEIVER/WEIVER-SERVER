package com.weiver.essay.dto.request;

import jakarta.validation.constraints.NotBlank;

public record EssayAnswerUpdateRequestDTO(
        @NotBlank String answer
){}
