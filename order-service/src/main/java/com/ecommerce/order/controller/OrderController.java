package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order placement and lifecycle")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place an order",
            description = "Prices are fetched from product-service; the request never carries prices.")
    @ApiResponse(responseCode = "201", description = "Order created in status PENDING")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "422",
            description = "Unknown or inactive customer/address/product, or a cart mixing currencies")
    @ApiResponse(responseCode = "503", description = "user-service or product-service is unavailable")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.createOrder(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order by id",
            description = "Served entirely from this service's own data; no downstream calls.")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return orderService.getOrder(id);
    }

    /**
     * PATCH, not PUT: this is a state-transition command, not a replacement of the resource.
     */
    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order",
            description = "Allowed before payment only. Repeating it on an already-cancelled order is a no-op.")
    @ApiResponse(responseCode = "409", description = "The order can no longer be cancelled")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return orderService.cancelOrder(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move an order to a new status",
            description = "Only transitions allowed by the order state machine are accepted.")
    @ApiResponse(responseCode = "409", description = "Illegal transition from the current status")
    public OrderResponse updateStatus(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }
}
