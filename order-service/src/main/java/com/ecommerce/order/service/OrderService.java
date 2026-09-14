package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.client.UserServiceClient;
import com.ecommerce.order.client.dto.AddressDto;
import com.ecommerce.order.client.dto.CustomerDto;
import com.ecommerce.order.client.dto.ProductDto;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.OrderSummaryResponse;
import com.ecommerce.order.dto.PageResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.entity.ShippingAddress;
import com.ecommerce.order.exception.DownstreamNotFoundException;
import com.ecommerce.order.exception.InvalidOrderStateException;
import com.ecommerce.order.exception.ResourceNotFoundException;
import com.ecommerce.order.exception.UnprocessableRequestException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Order use cases.
 *
 * <p>The interesting method is {@link #createOrder}, which is the only place in the platform
 * that coordinates across services. Read the comments there in order - the SEQUENCE of steps is
 * the design, not an accident.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserServiceClient userServiceClient;
    private final ProductServiceClient productServiceClient;
    private final OrderMapper orderMapper;

    /**
     * Places an order.
     *
     * <p>Deliberately NOT annotated {@code @Transactional}. A transaction starts when an
     * annotated method is entered, so marking this method would hold a database connection open
     * for the whole duration of three remote HTTP calls. Under load that exhausts the
     * connection pool, and a slow downstream service becomes a database outage.
     *
     * <p>Instead every remote call happens first, and the single
     * {@code orderRepository.save(order)} at the end is itself transactional (Spring Data's
     * repository implementation is annotated), persisting the order and its cascaded items in
     * one short transaction.
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        // --- Step 1: the customer must exist and be active ---
        CustomerDto customer = fetchCustomer(request.customerId());
        if (!customer.isActive()) {
            throw new UnprocessableRequestException(
                    "Customer '%s' is not active and cannot place orders".formatted(request.customerId()));
        }

        // --- Step 2: the shipping address must exist AND belong to this customer.
        // user-service enforces the ownership check via its nested URL, so a 404 here means
        // either "no such address" or "not this customer's address" - both are our 422. ---
        AddressDto address = fetchAddress(request.customerId(), request.shippingAddressId());

        // --- Step 3: price the cart. ONE batch call for all lines, never one call per line. ---
        List<UUID> productIds = distinctProductIds(request.items());
        Map<UUID, ProductDto> productsById = fetchProducts(productIds);

        validateAllProductsUsable(productIds, productsById);
        String currency = singleCurrency(productsById.values());

        // --- Step 4: build the order. Prices come from productsById (product-service's
        // answer), never from the request. The address and product details are COPIED in,
        // so the order still reads correctly if either changes later. ---
        Order order = new Order(request.customerId(), currency, snapshotOf(address));
        for (OrderItemRequest item : request.items()) {
            ProductDto product = productsById.get(item.productId());
            order.addItem(product.id(), product.sku(), product.name(), item.quantity(), product.price());
        }

        // --- Step 5: one local write. No distributed transaction: order-service commits its
        // own data and nothing else. ---
        Order saved = orderRepository.save(order);

        log.info("Created order id={} customerId={} lines={} total={} {}",
                saved.getId(), saved.getCustomerId(), saved.getItems().size(),
                saved.getTotalAmount(), saved.getCurrency());
        return orderMapper.toResponse(saved);
    }

    /**
     * Reads work with product-service and user-service switched off, because everything needed
     * to render an order was snapshotted at order time.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getCustomerOrders(UUID customerId, OrderStatus status,
                                                                Pageable pageable) {
        Page<Order> page = (status == null)
                ? orderRepository.findAllByCustomerId(customerId, pageable)
                : orderRepository.findAllByCustomerIdAndStatus(customerId, status, pageable);
        return PageResponse.from(page.map(orderMapper::toSummaryResponse));
    }

    /**
     * Cancels an order. Only legal before payment; afterwards it needs a refund flow, which is
     * a later phase.
     */
    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));

        // Already cancelled -> return as-is rather than failing, so a repeated cancel is
        // idempotent for the caller.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return orderMapper.toResponse(order);
        }

        try {
            order.cancel();
        } catch (IllegalStateException ex) {
            // The domain speaks in domain terms; the service translates to an HTTP concern.
            throw new InvalidOrderStateException(ex.getMessage());
        }

        log.info("Cancelled order id={}", id);
        return orderMapper.toResponse(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatus target) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));

        try {
            order.transitionTo(target);
        } catch (IllegalStateException ex) {
            throw new InvalidOrderStateException(ex.getMessage());
        }

        log.info("Order id={} moved to {}", id, target);
        return orderMapper.toResponse(orderRepository.saveAndFlush(order));
    }

    // ------------------------------------------------------------------
    // Remote calls. Each one translates a downstream 404 into OUR 422, with a message that
    // says what was actually wrong. Timeouts and 5xx are left to propagate: the Feign error
    // decoder turns them into DownstreamServiceException, which the handler maps to 503.
    // ------------------------------------------------------------------

    private CustomerDto fetchCustomer(UUID customerId) {
        try {
            return userServiceClient.getCustomer(customerId);
        } catch (DownstreamNotFoundException ex) {
            throw new UnprocessableRequestException("Customer '%s' does not exist".formatted(customerId));
        }
    }

    private AddressDto fetchAddress(UUID customerId, UUID addressId) {
        try {
            return userServiceClient.getAddress(customerId, addressId);
        } catch (DownstreamNotFoundException ex) {
            throw new UnprocessableRequestException(
                    "Address '%s' does not exist for customer '%s'".formatted(addressId, customerId));
        }
    }

    private Map<UUID, ProductDto> fetchProducts(List<UUID> productIds) {
        List<ProductDto> products = productServiceClient.lookupProducts(productIds);
        return products.stream().collect(Collectors.toMap(ProductDto::id, Function.identity()));
    }

    // ------------------------------------------------------------------
    // Cart validation
    // ------------------------------------------------------------------

    /** LinkedHashSet: de-duplicates while keeping request order, so messages are predictable. */
    private static List<UUID> distinctProductIds(List<OrderItemRequest> items) {
        return List.copyOf(new LinkedHashSet<>(items.stream().map(OrderItemRequest::productId).toList()));
    }

    /**
     * product-service simply omits ids it does not know, so "asked for 3, got 2" means one is
     * unknown. Inactive products are reported the same way: the cart cannot be fulfilled.
     * Both cases name the offending ids, so the caller can fix the cart rather than guess.
     */
    private static void validateAllProductsUsable(List<UUID> requestedIds, Map<UUID, ProductDto> found) {
        List<UUID> unknown = requestedIds.stream().filter(id -> !found.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new UnprocessableRequestException("Unknown product(s): " + unknown);
        }

        List<UUID> inactive = found.values().stream()
                .filter(product -> !product.isActive())
                .map(ProductDto::id)
                .toList();
        if (!inactive.isEmpty()) {
            throw new UnprocessableRequestException("Product(s) no longer available: " + inactive);
        }
    }

    /**
     * An order carries ONE currency and one total, so a cart mixing currencies cannot be priced.
     * Summing 10 USD and 10 CAD into "20" would be silently wrong - far worse than a 422.
     */
    private static String singleCurrency(java.util.Collection<ProductDto> products) {
        Set<String> currencies = products.stream().map(ProductDto::currency).collect(Collectors.toSet());
        if (currencies.size() > 1) {
            throw new UnprocessableRequestException(
                    "All items in an order must share one currency, but found: " + currencies);
        }
        return currencies.iterator().next();
    }

    private static ShippingAddress snapshotOf(AddressDto address) {
        return new ShippingAddress(
                address.id(),
                address.addressLine1(),
                address.addressLine2(),
                address.city(),
                address.state(),
                address.postalCode(),
                address.country());
    }
}
