package com.ecommerce.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Customer aggregate root.
 *
 * <p>There are deliberately no public setters. State changes go through intention-revealing
 * methods ({@link #updateProfile}, {@link #deactivate}) so rules such as "new customers start
 * ACTIVE" live in one place instead of being scattered across callers.
 */
@Entity
@Table(name = "customers")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA needs it; application code should not use it
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Always stored lower-case; the service normalises it and the database enforces it. */
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    /** BCrypt hash. Never mapped into any response DTO. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status;

    /** Drives authorization. Set at registration; changing it is an admin action, not self-service. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /** Optimistic locking: concurrent updates to the same row fail instead of silently overwriting. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Customer(String firstName, String lastName, String email, String phone, String passwordHash) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.status = CustomerStatus.ACTIVE;
        // Public registration always creates a shopper. An ADMIN is made deliberately,
        // never by anyone who can reach the signup form.
        this.role = Role.CUSTOMER;
    }

    public void updateProfile(String firstName, String lastName, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    public void deactivate() {
        this.status = CustomerStatus.INACTIVE;
    }

    public boolean isActive() {
        return status == CustomerStatus.ACTIVE;
    }
}
