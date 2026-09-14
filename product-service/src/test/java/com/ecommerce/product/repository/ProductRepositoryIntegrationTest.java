package com.ecommerce.product.repository;

import com.ecommerce.product.configuration.JpaAuditingConfig;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.entity.ProductStatus;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL via Testcontainers, running the real Flyway migrations. This is what proves
 * the Specification-based search actually produces valid SQL and that the CHECK constraints
 * behave as intended.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
class ProductRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void save_assignsIdAuditTimestampsAndVersion() {
        Product saved = productRepository.saveAndFlush(product("SKU-AUDIT-1", "Tee", new BigDecimal("24.99")));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void duplicateSku_isRejectedByUniqueConstraint() {
        productRepository.saveAndFlush(product("SKU-DUP-1", "Tee", new BigDecimal("24.99")));

        assertThatThrownBy(() ->
                productRepository.saveAndFlush(product("SKU-DUP-1", "Another tee", new BigDecimal("19.99"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void negativePrice_isRejectedByCheckConstraint() {
        assertThatThrownBy(() ->
                productRepository.saveAndFlush(product("SKU-NEG-1", "Tee", new BigDecimal("-1.00"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void search_combinesKeywordAndPriceRange() {
        productRepository.save(product("SKU-S-1", "Blue running shoe", new BigDecimal("80.00")));
        productRepository.save(product("SKU-S-2", "Blue dress shirt", new BigDecimal("40.00")));
        productRepository.save(product("SKU-S-3", "Red cap", new BigDecimal("15.00")));
        productRepository.flush();

        Page<Product> page = productRepository.findAll(
                ProductSpecifications.search("blue", null, ProductStatus.ACTIVE,
                        new BigDecimal("50.00"), null),
                PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Product::getSku).containsExactly("SKU-S-1");
    }

    @Test
    void findAllByIdIn_returnsOnlyRequestedProducts() {
        Product one = productRepository.save(product("SKU-B-1", "Tee", new BigDecimal("24.99")));
        productRepository.save(product("SKU-B-2", "Cap", new BigDecimal("14.99")));
        productRepository.flush();

        List<Product> found = productRepository.findAllByIdIn(List.of(one.getId()));

        assertThat(found).extracting(Product::getSku).containsExactly("SKU-B-1");
    }

    private Product product(String sku, String name, BigDecimal price) {
        Category category = categoryRepository.save(new Category("Cat-" + sku, null));
        return new Product(sku, name, null, price, "CAD", category);
    }
}
