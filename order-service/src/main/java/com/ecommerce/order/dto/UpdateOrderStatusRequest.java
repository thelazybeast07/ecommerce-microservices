package com.ecommerce.order.dto;

import com.ecommerce.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(

        @Schema(example = "SHIPPED", description = "Target status; must be a legal transition")
        @NotNull
        OrderStatus status
) {
}
