package com.ecommerce.product.entity;

public enum CategoryStatus {
    ACTIVE,
    /** Soft-deleted. Existing products may still reference it; new products may not. */
    INACTIVE
}
