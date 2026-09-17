package com.ecommerce.user.entity;

/**
 * What a caller is allowed to do, as opposed to who they are.
 *
 * <p>Deliberately small. Two roles cover every rule we need; inventing MANAGER, SUPPORT and so
 * on before anything requires them only adds branches nobody tests.
 */
public enum Role {
    /** A shopper. May read the catalogue and manage only their own data. */
    CUSTOMER,
    /** Staff. May manage the catalogue and read any customer's data. */
    ADMIN
}
