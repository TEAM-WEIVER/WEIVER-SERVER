package com.weiver.matching.dto.request;

import jakarta.validation.constraints.NotNull;

public record ApplicantContactRequestDTO(
        @NotNull(message = "지원자 Id는 필수 전달값입니다.")
        Long applicantId,
        @NotNull(message = "채용 공고 Id는 필수 전달값입니다.")
        Long jdId
) {
}
