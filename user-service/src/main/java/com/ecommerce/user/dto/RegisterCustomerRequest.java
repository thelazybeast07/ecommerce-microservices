package com.ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterCustomerRequest(

        @Schema(example = "Jane")
        @NotBlank @Size(max = 100)
        String firstName,

        @Schema(example = "Doe")
        @NotBlank @Size(max = 100)
        String lastName,

        @Schema(example = "jane.doe@example.com")
        @NotBlank @Email @Size(max = 254)
        String email,

        @Schema(example = "+14165550123", description = "Optional. International format.")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone,

        // BCrypt only uses the first 72 bytes of input, hence the upper bound.
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, example = "correct-horse-battery")
        @NotBlank @Size(min = 8, max = 72)
        String password
) {

    /** Records generate toString() from every component. Never let a password reach a log line. */
    @Override
    public String toString() {
        return "RegisterCustomerRequest[firstName=%s, lastName=%s, email=%s, phone=%s, password=****]"
                .formatted(firstName, lastName, email, phone);
    }
}
