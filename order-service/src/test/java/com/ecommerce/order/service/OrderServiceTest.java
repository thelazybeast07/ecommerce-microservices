package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.client.UserServiceClient;
import com.ecommerce.order.client.dto.AddressDto;
import com.ecommerce.order.client.dto.CustomerDto;
import com.ecommerce.order.client.dto.ProductDto;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.exception.DownstreamNotFoundException;
import com.ecommerce.order.exception.DownstreamServiceException;
import com.ecommerce.order.exception.InvalidOrderStateException;
import com.ecommerce.order.exception.UnprocessableRequestException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The service's cross-service logic, with both Feign clients mocked. No network, no database.
 *
 * <p>Mocking the Feign interfaces is exactly why they are interfaces: the orchestration can be
 * tested against every downstream outcome (missing, inactive, timeout) without running or
 * breaking the real services.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private ProductServiceClient productServiceClient;

    private OrderService orderService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID productA = UUID.randomUUID();
    private final UUID productB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, userServiceClient, productServiceClient, new OrderMapper());
    }

    @Test
    @DisplayName("prices come from product-service and the total is computed, not trusted")
    void createOrder_pricesFromCatalogue() {
        givenActiveCustomerAndAddress();
        when(productServiceClient.lookupProducts(anyList())).thenReturn(List.of(
                product(productA, "SKU-A", "Tee", "24.99", "CAD", "ACTIVE"),
                product(productB, "SKU-B", "Cap", "10.00", "CAD", "ACTIVE")));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.createOrder(new CreateOrderRequest(
                customerId, addressId,
                List.of(new OrderItemRequest(productA, 2), new OrderItemRequest(productB, 1))));

        // 2 x 24.99 + 1 x 10.00
        assertThat(response.totalAmount()).isEqualByComparingTo("59.98");
        assertThat(response.currency()).isEqualTo("CAD");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().unitPrice()).isEqualByComparingTo("24.99");
    }

    @Test
    @DisplayName("the shipping address is snapshotted onto the order, not referenced")
    void createOrder_snapshotsShippingAddress() {
        givenActiveCustomerAndAddress();
        when(productServiceClient.lookupProducts(anyList()))
                .thenReturn(List.of(product(productA, "SKU-A", "Tee", "24.99", "CAD", "ACTIVE")));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.createOrder(new CreateOrderRequest(
                customerId, addressId, List.of(new OrderItemRequest(productA, 1))));

        assertThat(response.shippingAddress().city()).isEqualTo("Toronto");
        assertThat(response.shippingAddress().sourceAddressId()).isEqualTo(addressId);
    }

    @Test
    void createOrder_inactiveCustomer_is422_andNeverCallsProductService() {
        when(userServiceClient.getCustomer(customerId)).thenReturn(new CustomerDto(customerId, "INACTIVE"));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                customerId, addressId, List.of(new OrderItemRequest(productA, 1)))))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("not active");

        verifyNoInteractions(productServiceClient);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("a downstream 404 becomes our 422, naming what was missing")
    void createOrder_unknownCustomer_is422() {
        when(userServiceClient.getCustomer(customerId))
                .thenThrow(new DownstreamNotFoundException("not found"));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                customerId, addressId, List.of(new OrderItemRequest(productA, 1)))))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining(customerId.toString());
    }

    @Test
    void createOrder_addressNotOwnedByCustomer_is422() {
        when(userServiceClient.getCustomer(customerId)).thenReturn(new CustomerDto(customerId, "ACTIVE"));
        when(userServiceClient.getAddress(customerId, addressId))
                .thenThrow(new DownstreamNotFoundException("not found"));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                customerId, addressId, List.of(new OrderItemRequest(productA, 1)))))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining(addressId.toString());
    }

    @Test
    @DisplayName("an id product-service omits from its answer means an unknown product")
    void createOrder_unknownProduct_is422() {
        givenActiveCustomerAndAddress();
        when(productServiceClient.lookupProducts(anyList()))
                .thenReturn(List.of(product(productA, "SKU-A", "Tee", "24.99", "CAD", "ACTIVE")));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                customerId, addressId,
                List.of(new OrderItemRequest(productA, 1), new OrderItemRequest(productB, 1)))))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining(productB.toString());

        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_discontinuedProduct_is422() {
        givenActiveCustomerAndAddress();
        when(productServiceClient.lookupProducts(anyList()))
                .thenReturn(List.of(product(productA, "SKU-A", "Tee", "24.99", "CAD", "INACTIVE")));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                customerId, addressId, List.of(new OrderItemRequest(productA, 1)))))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    @DisplayName("a cart mixing currencies is rejected rather than silently mis-totalled")
    void createOrder_mixedCurrencies_is422() {
        givenActiveCustomerAndAddress();
        when(productServiceClient.lookupProducts(anyList())).thenReturn(List.of(
                product(productA, "SKU-A", "Tee", "24.99", "CAD", "ACTIVE"),
                product(productB, "SKU-B", "Cap", "10.00", "USD", "ACTIVE")));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                customerId, addressId,
                List.of(new OrderItemRequest(productA, 1), new OrderItemRequest(productB, 1)))))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("one currency");
    }

    @Test
    @DisplayName("a downstream outage propagates as DownstreamServiceException -> 503, not 500")
    void createOrder_downstreamDown_propagates() {
        when(userServiceClient.getCustomer(customerId))
                .thenThrow(new DownstreamServiceException("user-service unavailable"));

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(
                customerId, addressId, List.of(new OrderItemRequest(productA, 1)))))
                .isInstanceOf(DownstreamServiceException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("the same product twice is looked up once, but both lines are kept")
    void createOrder_duplicateProductIds_areLookedUpOnce() {
        givenActiveCustomerAndAddress();
        when(productServiceClient.lookupProducts(List.of(productA)))
                .thenReturn(List.of(product(productA, "SKU-A", "Tee", "10.00", "CAD", "ACTIVE")));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.createOrder(new CreateOrderRequest(
                customerId, addressId,
                List.of(new OrderItemRequest(productA, 1), new OrderItemRequest(productA, 2))));

        verify(productServiceClient).lookupProducts(List.of(productA));
        assertThat(response.totalAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void cancelOrder_afterPayment_is409() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingOrder();
        order.transitionTo(OrderStatus.CONFIRMED);
        order.transitionTo(OrderStatus.PAYMENT_PENDING);
        order.transitionTo(OrderStatus.PAID);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId, customerId, false))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    @DisplayName("cancelling an already-cancelled order returns it unchanged rather than failing")
    void cancelOrder_isIdempotent() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingOrder();
        order.cancel();
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancelOrder(orderId,customerId, false);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateStatus_illegalTransition_is409() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(pendingOrder()));

        assertThatThrownBy(() -> orderService.updateStatus(orderId, OrderStatus.DELIVERED))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("PENDING");
    }

    // ---------- helpers ----------

    private void givenActiveCustomerAndAddress() {
        when(userServiceClient.getCustomer(customerId)).thenReturn(new CustomerDto(customerId, "ACTIVE"));
        when(userServiceClient.getAddress(eq(customerId), eq(addressId))).thenReturn(new AddressDto(
                addressId, customerId, "100 King St W", null, "Toronto", "ON", "M5X 1A9", "CA"));
    }

    private static ProductDto product(UUID id, String sku, String name, String price,
                                      String currency, String status) {
        return new ProductDto(id, sku, name, new BigDecimal(price), currency, status);
    }

    private Order pendingOrder() {
        Order order = new Order(customerId, "CAD", new com.ecommerce.order.entity.ShippingAddress(
                addressId, "100 King St W", null, "Toronto", "ON", "M5X 1A9", "CA"));
        order.addItem(productA, "SKU-A", "Tee", 1, new BigDecimal("24.99"));
        return order;
    }
}
