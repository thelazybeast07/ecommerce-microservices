package com.ecommerce.order.repository;

import com.ecommerce.order.configuration.JpaAuditingConfig;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.entity.ShippingAddress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class OrderRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderRepository orderRepository;

    private final UUID customerId = UUID.randomUUID();

    @Test
    void save_cascadesItemsAndComputesTotal() {
        Order order = orderWithTwoItems();

        Order saved = orderRepository.saveAndFlush(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getItems()).allSatisfy(item -> assertThat(item.getId()).isNotNull());
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("59.98");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void findWithItemsById_loadsItemsInTheSameQuery() {
        UUID id = orderRepository.saveAndFlush(orderWithTwoItems()).getId();

        Order found = orderRepository.findWithItemsById(id).orElseThrow();

        assertThat(found.getItems()).hasSize(2);
    }

    @Test
    void theSameProductTwiceInOneOrder_isRejectedByUniqueConstraint() {
        UUID productId = UUID.randomUUID();
        Order order = newOrder();
        order.addItem(productId, "SKU-A", "Tee", 1, new BigDecimal("10.00"));
        order.addItem(productId, "SKU-A", "Tee", 2, new BigDecimal("10.00"));

        assertThatThrownBy(() -> orderRepository.saveAndFlush(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findAllByCustomerIdAndStatus_filters() {
        orderRepository.save(orderWithTwoItems());
        Order cancelled = orderWithTwoItems();
        cancelled.cancel();
        orderRepository.save(cancelled);
        orderRepository.flush();

        Page<Order> page = orderRepository.findAllByCustomerIdAndStatus(
                customerId, OrderStatus.CANCELLED, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private Order newOrder() {
        return new Order(customerId, "CAD", new ShippingAddress(
                UUID.randomUUID(), "100 King St W", null, "Toronto", "ON", "M5X 1A9", "CA"));
    }

    private Order orderWithTwoItems() {
        Order order = newOrder();
        order.addItem(UUID.randomUUID(), "SKU-A", "Tee", 2, new BigDecimal("24.99"));
        order.addItem(UUID.randomUUID(), "SKU-B", "Cap", 1, new BigDecimal("10.00"));
        return order;
    }
}
