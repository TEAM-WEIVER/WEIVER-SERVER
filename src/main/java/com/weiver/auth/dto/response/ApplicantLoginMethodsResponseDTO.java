package com.weiver.auth.dto.response;

public record ApplicantLoginMethodsResponseDTO(
        boolean passwordEnabled,
        boolean kakaoLinked
) {
}
