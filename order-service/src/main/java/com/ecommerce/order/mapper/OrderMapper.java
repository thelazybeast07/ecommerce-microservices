package com.ecommerce.order.mapper;

import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.OrderSummaryResponse;
import com.ecommerce.order.dto.ShippingAddressResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.ShippingAddress;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    /** Full detail, including items. Requires the order to have been loaded with its items. */
    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                toAddressResponse(order.getShippingAddress()),
                order.getItems().stream().map(this::toItemResponse).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    /** List view. Must never touch getItems() - see OrderSummaryResponse. */
    public OrderSummaryResponse toSummaryResponse(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductSku(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal());
    }

    private ShippingAddressResponse toAddressResponse(ShippingAddress address) {
        return new ShippingAddressResponse(
                address.getSourceAddressId(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry());
    }
}
