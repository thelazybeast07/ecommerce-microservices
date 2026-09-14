package com.ecommerce.product.mapper;

import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.ProductLookupResponse;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    /**
     * The normalised SKU and the resolved {@link Category} are passed in rather than derived
     * here: normalising and validating them are business decisions that belong in the service.
     */
    public Product toEntity(CreateProductRequest request, String normalizedSku, Category category) {
        return new Product(
                normalizedSku,
                request.name(),
                request.description(),
                request.price(),
                request.currency(),
                category);
    }

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.getCategory().getId(),
                // Touches the lazy proxy: safe only inside a transaction, which is where
                // mapping happens because the service returns DTOs, never entities.
                product.getCategory().getName(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public ProductLookupResponse toLookupResponse(Product product) {
        return new ProductLookupResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getPrice(),
                product.getCurrency(),
                product.getStatus());
    }
}
