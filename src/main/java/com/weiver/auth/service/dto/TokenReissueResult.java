package com.weiver.auth.service.dto;

public record TokenReissueResult(
        String accessToken,
        String refreshToken
) {
}
