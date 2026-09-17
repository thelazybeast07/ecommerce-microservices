package com.ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param accessToken the signed JWT
 * @param tokenType   always "Bearer" - the caller sends "Authorization: Bearer &lt;token&gt;"
 * @param expiresIn   seconds until the token stops being accepted
 */
public record TokenResponse(
        @Schema(description = "Send on later requests as: Authorization: Bearer <accessToken>")
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
