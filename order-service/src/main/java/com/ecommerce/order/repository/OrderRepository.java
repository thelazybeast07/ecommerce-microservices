package com.ecommerce.order.repository;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * {@code @EntityGraph} tells Hibernate to fetch the items in the SAME query as the order,
     * instead of a second query when the items are first touched. Without it, rendering an
     * order's lines triggers the N+1 problem.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);

    /**
     * Deliberately NOT using @EntityGraph here. Combining a collection fetch-join with
     * pagination forces Hibernate to load every matching row into memory and paginate there
     * (it warns "firstResult/maxResults specified with collection fetch"). For a list view the
     * items are summarised, not enumerated, so a plain paged query is correct.
     */
    Page<Order> findAllByCustomerId(UUID customerId, Pageable pageable);

    Page<Order> findAllByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);
}
