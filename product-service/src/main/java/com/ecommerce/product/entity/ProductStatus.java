package com.ecommerce.product.entity;

public enum ProductStatus {
    ACTIVE,
    /** Soft-deleted / discontinued. Kept so past orders still resolve it; hidden from catalogue search. */
    INACTIVE
}
