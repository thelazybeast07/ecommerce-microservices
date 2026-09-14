package com.ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PUT semantics: the full set of updatable fields. Email is intentionally absent; changing the
 * login identity needs a verification flow that arrives with authentication.
 */
public record UpdateCustomerRequest(

        @Schema(example = "Jane")
        @NotBlank @Size(max = 100)
        String firstName,

        @Schema(example = "Doe")
        @NotBlank @Size(max = 100)
        String lastName,

        @Schema(example = "+14165550123")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone
) {
}
