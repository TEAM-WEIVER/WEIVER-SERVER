package com.weiver.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record CsrfTokenResponse(
        @Schema(description = "CSRF 토큰", example = "abc123")
        String csrfToken
) {
}
