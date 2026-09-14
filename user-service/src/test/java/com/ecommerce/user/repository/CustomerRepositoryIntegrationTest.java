package com.ecommerce.user.repository;

import com.ecommerce.user.configuration.JpaAuditingConfig;
import com.ecommerce.user.entity.Customer;
import com.ecommerce.user.entity.CustomerStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository slice against a REAL PostgreSQL (Testcontainers), with the real Flyway migrations.
 * This is what proves the entity mappings, constraints and derived queries agree with the schema.
 * An in-memory H2 database would not enforce PostgreSQL-specific behaviour. Requires Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class) // slice tests do not scan @Configuration classes
@Testcontainers
class CustomerRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void save_assignsIdAuditTimestampsAndVersion() {
        Customer saved = customerRepository.saveAndFlush(newCustomer("audit@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void duplicateEmail_isRejectedByUniqueConstraint() {
        customerRepository.saveAndFlush(newCustomer("dup@example.com"));

        assertThatThrownBy(() -> customerRepository.saveAndFlush(newCustomer("dup@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mixedCaseEmail_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> customerRepository.saveAndFlush(newCustomer("Mixed.Case@Example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findAllByStatus_returnsOnlyMatchingCustomers() {
        customerRepository.save(newCustomer("active@example.com"));
        Customer inactive = newCustomer("inactive@example.com");
        inactive.deactivate();
        customerRepository.save(inactive);
        customerRepository.flush();

        Page<Customer> page = customerRepository.findAllByStatus(CustomerStatus.INACTIVE, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Customer::getEmail).containsExactly("inactive@example.com");
    }

    private static Customer newCustomer(String email) {
        return new Customer("Test", "User", email, null, "bcrypt-hash");
    }
}
