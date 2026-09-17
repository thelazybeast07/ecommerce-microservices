package com.ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @Schema(example = "jane.doe@example.com")
        @NotBlank
        String email,

        // Deliberately no @Size. Rejecting a login for being "too short" would reveal the
        // password rules to someone who has not authenticated.
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, example = "correct-horse-battery")
        @NotBlank
        String password
) {

    /** Records print every field in toString(); a password must never reach a log line. */
    @Override
    public String toString() {
        return "LoginRequest[email=%s, password=****]".formatted(email);
    }
}
