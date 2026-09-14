package com.ecommerce.order.integration;

import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.client.UserServiceClient;
import com.ecommerce.order.client.dto.AddressDto;
import com.ecommerce.order.client.dto.CustomerDto;
import com.ecommerce.order.client.dto.ProductDto;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack against real PostgreSQL, with the two Feign clients replaced by mocks.
 *
 * <p>Why mock them: this test is about ORDER-service - its HTTP layer, its orchestration, its
 * persistence. Requiring two other services to be running would make it a flaky end-to-end test
 * that fails for reasons unrelated to the code under test.
 *
 * <p>The gap this leaves is honest and worth naming: it does not prove our Feign interfaces
 * match the real services' contracts. WireMock (stubbing real HTTP) or Spring Cloud Contract
 * (generating tests from a shared contract) is the proper next step, and a good thing to
 * mention in an interview as the thing you would add.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private ProductServiceClient productServiceClient;

    private final UUID customerId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Test
    void orderLifecycle_placeReadAdvanceThroughStatuses() throws Exception {
        givenHealthyDownstreams();

        String body = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartJson(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(49.98))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.id");

        // Reading the order does not call the other services at all: everything was snapshotted
        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productSku").value("SKU-A"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(24.99))
                .andExpect(jsonPath("$.shippingAddress.city").value("Toronto"));

        mockMvc.perform(get("/api/v1/customers/{customerId}/orders", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                // the summary view deliberately omits line items
                .andExpect(jsonPath("$.content[0].items").doesNotExist());

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Skipping straight to DELIVERED is not a legal transition from CONFIRMED
        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DELIVERED"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelBeforePayment_succeeds_andIsIdempotent() throws Exception {
        givenHealthyDownstreams();

        String body = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartJson(1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.id");

        mockMvc.perform(patch("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(patch("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void placingAnOrderForADiscontinuedProduct_returns422_andPersistsNothing() throws Exception {
        when(userServiceClient.getCustomer(any())).thenReturn(new CustomerDto(customerId, "ACTIVE"));
        when(userServiceClient.getAddress(any(), any())).thenReturn(address());
        when(productServiceClient.lookupProducts(anyList()))
                .thenReturn(List.of(new ProductDto(productId, "SKU-A", "Tee",
                        new BigDecimal("24.99"), "CAD", "INACTIVE")));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartJson(1)))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/customers/{customerId}/orders", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private void givenHealthyDownstreams() {
        when(userServiceClient.getCustomer(any())).thenReturn(new CustomerDto(customerId, "ACTIVE"));
        when(userServiceClient.getAddress(any(), any())).thenReturn(address());
        when(productServiceClient.lookupProducts(anyList()))
                .thenReturn(List.of(new ProductDto(productId, "SKU-A", "Tee",
                        new BigDecimal("24.99"), "CAD", "ACTIVE")));
    }

    private AddressDto address() {
        return new AddressDto(addressId, customerId, "100 King St W", null,
                "Toronto", "ON", "M5X 1A9", "CA");
    }

    private String cartJson(int quantity) {
        return """
                {"customerId":"%s","shippingAddressId":"%s",
                 "items":[{"productId":"%s","quantity":%d}]}
                """.formatted(customerId, addressId, productId, quantity);
    }
}
