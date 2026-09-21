package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderSummaryResponse;
import com.ecommerce.order.dto.PageResponse;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Lives in ORDER-service even though the URL starts with /customers.
 *
 * <p>Ownership follows the data, not the path: orders belong to this service, so this service
 * answers questions about them. When an API gateway is introduced it will route
 * {@code /api/v1/customers/*} /orders here and everything else under /customers to user-service.
 *
 * <p>The customer id is not verified against user-service. Doing so would add a remote call to
 * a read that does not need one, and an unknown customer simply has no orders - an empty page
 * is the honest answer.
 */
@PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal")
@RestController
@RequestMapping("/api/v1/customers/{customerId}/orders")
@RequiredArgsConstructor
@Tag(name = "Customer orders", description = "A customer's order history")
public class CustomerOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "List a customer's orders (paged, newest first)",
            description = "Summary view without line items.")
    public PageResponse<OrderSummaryResponse> getCustomerOrders(
            @PathVariable UUID customerId,
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return orderService.getCustomerOrders(customerId, status, pageable);
    }
}
