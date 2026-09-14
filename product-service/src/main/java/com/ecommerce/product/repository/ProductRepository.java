package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

/**
 * {@code JpaSpecificationExecutor} is used instead of derived query methods (as in user-service)
 * because product search combines several OPTIONAL filters (keyword, category, status, price
 * range) that can each be present or absent in any combination. A derived method name for every
 * combination would be unworkable; {@link ProductSpecifications} builds the WHERE clause
 * dynamically instead. See the search endpoint in ProductService for how it's used.
 */
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    boolean existsBySku(String sku);

    /** Batch lookup used by order-service so it can price a whole cart in one call, not one per item. */
    List<Product> findAllByIdIn(List<UUID> ids);
}
