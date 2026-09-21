package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.ShippingAddressResponse;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.exception.DownstreamServiceException;
import com.ecommerce.order.exception.InvalidOrderStateException;
import com.ecommerce.order.exception.ResourceNotFoundException;
import com.ecommerce.order.exception.UnprocessableRequestException;
import com.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createOrder_valid_returns201WithLocation() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.createOrder(any())).thenReturn(sampleOrder(orderId, OrderStatus.PENDING));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","shippingAddressId":"%s",
                                 "items":[{"productId":"%s","quantity":2}]}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/orders/" + orderId)))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(49.98));
    }

    @Test
    void createOrder_emptyCart_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","shippingAddressId":"%s","items":[]}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.field == 'items')]").exists());

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_zeroQuantity_returns400_becauseValidationCascadesIntoItems() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","shippingAddressId":"%s",
                                 "items":[{"productId":"%s","quantity":0}]}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field =~ /items.*quantity/)]").exists());
    }

    @Test
    void createOrder_unknownProduct_returns422() throws Exception {
        when(orderService.createOrder(any()))
                .thenThrow(new UnprocessableRequestException("Unknown product(s): [x]"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCartJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Unprocessable request"));
    }

    @Test
    void createOrder_downstreamDown_returns503_not500() throws Exception {
        when(orderService.createOrder(any()))
                .thenThrow(new DownstreamServiceException("product-service unavailable"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCartJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Service temporarily unavailable"));
    }

    @Test
    void getOrder_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.getOrder(id)).thenThrow(ResourceNotFoundException.of("Order", id));

        mockMvc.perform(get("/api/v1/orders/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void cancelOrder_illegalState_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.cancelOrder(eq(id), any(), anyBoolean()))
                .thenThrow(new InvalidOrderStateException("An order in status SHIPPED can no longer be cancelled"));

        mockMvc.perform(patch("/api/v1/orders/{id}/cancel", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid order state"));
    }

    @Test
    void updateStatus_returns200WithNewStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.updateStatus(id, OrderStatus.CONFIRMED))
                .thenReturn(sampleOrder(id, OrderStatus.CONFIRMED));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void updateStatus_unknownStatusValue_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"TELEPORTED"}"""))
                .andExpect(status().isBadRequest());
    }

    private static String validCartJson() {
        return """
                {"customerId":"%s","shippingAddressId":"%s",
                 "items":[{"productId":"%s","quantity":1}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static OrderResponse sampleOrder(UUID orderId, OrderStatus status) {
        return new OrderResponse(
                orderId, UUID.randomUUID(), status, new BigDecimal("49.98"), "CAD",
                new ShippingAddressResponse(UUID.randomUUID(), "100 King St W", null,
                        "Toronto", "ON", "M5X 1A9", "CA"),
                List.of(new OrderItemResponse(UUID.randomUUID(), UUID.randomUUID(), "SKU-A", "Tee",
                        2, new BigDecimal("24.99"), new BigDecimal("49.98"))),
                Instant.now(), Instant.now());
    }
}
