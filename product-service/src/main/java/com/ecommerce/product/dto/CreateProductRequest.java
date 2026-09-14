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

public record CreateProductRequest(

        @Schema(example = "TSHIRT-BLK-M")
        @NotBlank
        @Pattern(regexp = ValidationPatterns.SKU, message = ValidationPatterns.SKU_MESSAGE)
        String sku,

        @Schema(example = "Black cotton t-shirt (M)")
        @NotBlank @Size(max = 200)
        String name,

        @Schema(example = "180gsm combed cotton, regular fit")
        @Size(max = 2000)
        String description,

        // Digits pins the scale to 2 so a request cannot silently lose fractions of a cent.
        @Schema(example = "24.99")
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
