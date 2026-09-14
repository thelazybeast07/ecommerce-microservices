package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * One requested cart line. Notice there is NO price field: accepting a price from the client
 * would let anyone buy anything for a cent. Prices come from product-service.
 */
public record OrderItemRequest(

        @Schema(example = "6f1e4b9c-1c2d-4a3b-9e8f-7a6b5c4d3e2f")
        @NotNull
        UUID productId,

        @Schema(example = "2")
        @NotNull @Min(1) @Max(1000)
        Integer quantity
) {
}
