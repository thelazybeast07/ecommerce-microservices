package com.ecommerce.user.service;

import com.ecommerce.user.dto.CustomerProfileResponse;
import com.ecommerce.user.dto.CustomerResponse;
import com.ecommerce.user.dto.RegisterCustomerRequest;
import com.ecommerce.user.dto.UpdateCustomerRequest;
import com.ecommerce.user.entity.Address;
import com.ecommerce.user.entity.AddressType;
import com.ecommerce.user.entity.Customer;
import com.ecommerce.user.entity.CustomerStatus;
import com.ecommerce.user.exception.DuplicateResourceException;
import com.ecommerce.user.exception.InvalidResourceStateException;
import com.ecommerce.user.exception.ResourceNotFoundException;
import com.ecommerce.user.mapper.AddressMapper;
import com.ecommerce.user.mapper.CustomerMapper;
import com.ecommerce.user.repository.AddressRepository;
import com.ecommerce.user.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests: no Spring context, no database. Repositories and the encoder are mocked;
 * the mappers are real because they are simple, deterministic code worth exercising too.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, addressRepository,
                new CustomerMapper(), new AddressMapper(), passwordEncoder);
    }

    @Test
    @DisplayName("register normalises the email, hashes the password and saves an ACTIVE customer")
    void register_success() {
        var request = new RegisterCustomerRequest("Jane", "Doe", "Jane.Doe@Example.COM", "+14165550123", "correct-horse-battery");
        when(customerRepository.existsByEmail("jane.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("correct-horse-battery")).thenReturn("bcrypt-hash");
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.register(request);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(saved.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(response.email()).isEqualTo("jane.doe@example.com");
    }

    @Test
    @DisplayName("register rejects an email that is already registered, without hashing or saving")
    void register_duplicateEmail() {
        var request = new RegisterCustomerRequest("Jane", "Doe", "jane.doe@example.com", null, "correct-horse-battery");
        when(customerRepository.existsByEmail("jane.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.register(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(customerRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void getCustomer_notFound() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getProfile_includesAddresses() {
        UUID id = UUID.randomUUID();
        Customer customer = customerWithId(id);
        Address address = new Address(customer);
        address.setAddressLine1("100 King St W");
        address.setCity("Toronto");
        address.setPostalCode("M5X 1A9");
        address.setCountry("CA");
        address.setAddressType(AddressType.SHIPPING);
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));
        when(addressRepository.findAllByCustomerId(id)).thenReturn(List.of(address));

        CustomerProfileResponse profile = customerService.getProfile(id);

        assertThat(profile.customer().id()).isEqualTo(id);
        assertThat(profile.addresses()).singleElement()
                .satisfies(a -> {
                    assertThat(a.customerId()).isEqualTo(id);
                    assertThat(a.city()).isEqualTo("Toronto");
                });
    }

    @Test
    void updateCustomer_activeCustomer_updatesProfile() {
        UUID id = UUID.randomUUID();
        Customer customer = customerWithId(id);
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));
        when(customerRepository.saveAndFlush(customer)).thenReturn(customer);

        CustomerResponse response = customerService.updateCustomer(id,
                new UpdateCustomerRequest("Janet", "Smith", "+14165550199"));

        assertThat(response.firstName()).isEqualTo("Janet");
        assertThat(response.lastName()).isEqualTo("Smith");
        assertThat(response.phone()).isEqualTo("+14165550199");
    }

    @Test
    void updateCustomer_inactiveCustomer_isRejected() {
        UUID id = UUID.randomUUID();
        Customer customer = customerWithId(id);
        customer.deactivate();
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.updateCustomer(id,
                new UpdateCustomerRequest("Janet", "Smith", null)))
                .isInstanceOf(InvalidResourceStateException.class);

        verify(customerRepository, never()).saveAndFlush(any());
    }

    @Test
    void deactivateCustomer_marksInactive() {
        UUID id = UUID.randomUUID();
        Customer customer = customerWithId(id);
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        customerService.deactivateCustomer(id);

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
    }

    @Test
    @DisplayName("deactivating an already inactive customer is a no-op (idempotent DELETE)")
    void deactivateCustomer_isIdempotent() {
        UUID id = UUID.randomUUID();
        Customer customer = customerWithId(id);
        customer.deactivate();
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        customerService.deactivateCustomer(id);

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
    }

    /** The id is normally generated by Hibernate; in a unit test we set it directly. */
    static Customer customerWithId(UUID id) {
        Customer customer = new Customer("Jane", "Doe", "jane.doe@example.com", "+14165550123", "bcrypt-hash");
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }
}
