package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(

        @Schema(example = "c44caa67-1291-4fa0-8bbc-c1258188bc5d")
        @NotNull
        UUID customerId,

        @Schema(description = "An address id belonging to this customer, in user-service")
        @NotNull
        UUID shippingAddressId,

        /*
         * @Valid cascades validation INTO each element, so a line with quantity 0 is rejected.
         * Without it, only the list itself would be checked.
         */
        @NotEmpty(message = "an order must contain at least one item")
        @Size(max = 100, message = "an order may contain at most 100 lines")
        List<@Valid OrderItemRequest> items
) {
}
