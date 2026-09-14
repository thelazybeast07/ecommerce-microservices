package com.ecommerce.user.entity;

public enum CustomerStatus {
    ACTIVE,
    /** Soft-deleted. Kept for order history and auditing; read-only from the API's point of view. */
    INACTIVE
}
