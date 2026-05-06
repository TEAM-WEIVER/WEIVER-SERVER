package com.weiver.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReissueResponseDTO(
        @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken
) {
}
