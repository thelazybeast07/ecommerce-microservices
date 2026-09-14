package com.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * SKU is deliberately absent. It is the business identifier printed on labels and referenced by
 * warehouses; changing it on an existing product would break that link. Retire the product and
 * create a new one instead.
 */
public record UpdateProductRequest(

        @Schema(example = "Black cotton t-shirt (M)")
        @NotBlank @Size(max = 200)
        String name,

        @Schema(example = "180gsm combed cotton, regular fit")
        @Size(max = 2000)
        String description,

        @Schema(example = "26.99")
        @NotNull
        @DecimalMin(value = "0.00", message = "must not be negative")
        @Digits(integer = 17, fraction = 2)
        BigDecimal price,

        @Schema(example = "CAD")
        @NotBlank
        @Pattern(regexp = ValidationPatterns.CURRENCY, message = ValidationPatterns.CURRENCY_MESSAGE)
        String currency,

        @Schema(example = "0b7c6a14-6f4c-4c8e-9a1e-2f3f9c2b7a10")
        @NotNull
        UUID categoryId
) {
}
