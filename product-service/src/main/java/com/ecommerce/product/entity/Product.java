package com.ecommerce.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Stock keeping unit. Business identifier, unique, but never used as the primary key. */
    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    /** NUMERIC + BigDecimal, never double: floating point cannot represent money exactly. */
    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    /** ISO 4217, e.g. "USD", "CAD". */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * LAZY and unidirectional, same reasoning as Address -> Customer in user-service: reading a
     * product should not force-load its category, and Category has no {@code List<Product>}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Product(String sku, String name, String description, BigDecimal price, String currency, Category category) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.category = category;
        this.status = ProductStatus.ACTIVE;
    }

    public void updateDetails(String name, String description, BigDecimal price, String currency, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.category = category;
    }

    public void deactivate() {
        this.status = ProductStatus.INACTIVE;
    }

    public boolean isActive() {
        return status == ProductStatus.ACTIVE;
    }
}
