package com.ecommerce.user.dto;

import com.ecommerce.user.entity.AddressType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Used for both create (POST) and full replace (PUT) of an address. */
public record AddressRequest(

        @Schema(example = "100 King St W")
        @NotBlank @Size(max = 255)
        String addressLine1,

        @Schema(example = "Suite 500")
        @Size(max = 255)
        String addressLine2,

        @Schema(example = "Toronto")
        @NotBlank @Size(max = 100)
        String city,

        @Schema(example = "ON")
        @Size(max = 100)
        String state,

        @Schema(example = "M5X 1A9")
        @NotBlank @Size(max = 20)
        String postalCode,

        @Schema(example = "CA")
        @NotBlank
        @Pattern(regexp = ValidationPatterns.COUNTRY, message = ValidationPatterns.COUNTRY_MESSAGE)
        String country,

        @Schema(example = "SHIPPING")
        @NotNull
        AddressType addressType
) {
}
