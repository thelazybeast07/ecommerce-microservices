package com.ecommerce.user.controller;

import com.ecommerce.user.dto.CustomerProfileResponse;
import com.ecommerce.user.dto.CustomerResponse;
import com.ecommerce.user.dto.PageResponse;
import com.ecommerce.user.dto.RegisterCustomerRequest;
import com.ecommerce.user.dto.UpdateCustomerRequest;
import com.ecommerce.user.entity.CustomerStatus;
import com.ecommerce.user.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * HTTP adapter only: parse and validate input, call the service, choose the status code.
 * No business rules and no repository access in this class.
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer registration and profile management")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @Operation(summary = "Register a new customer")
    @ApiResponse(responseCode = "201", description = "Customer created; Location header points to it")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        CustomerResponse created = customerService.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Admin only. Listing every customer is a staff operation; a shopper has no business
     * enumerating other people's accounts.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "List customers (paged) - ADMIN only",
            description = "Optional status filter. Example: ?status=ACTIVE&page=0&size=20&sort=lastName,asc")
    public PageResponse<CustomerResponse> listCustomers(
            @RequestParam(required = false) CustomerStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return customerService.listCustomers(status, pageable);
    }

    /**
     * Your own record, or any record if you are an admin.
     *
     * <p>{@code authentication.principal} is the UUID our filter put in the SecurityContext.
     * Comparing it to the id in the URL is what stops customer A from reading customer B
     * simply by changing the number - the bug class known as IDOR (insecure direct object
     * reference), and the most common real-world API vulnerability there is.
     */
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal")
    @GetMapping("/{id}")
    @Operation(summary = "Get a customer by id - your own, or any if ADMIN")
    @ApiResponse(responseCode = "200", description = "Found")
    @ApiResponse(responseCode = "404", description = "No such customer")
    public CustomerResponse getCustomer(@PathVariable UUID id) {
        return customerService.getCustomer(id);
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal")
    @GetMapping("/{id}/profile")
    @Operation(summary = "Get a customer's profile including addresses - your own, or any if ADMIN")
    public CustomerProfileResponse getProfile(@PathVariable UUID id) {
        return customerService.getProfile(id);
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal")
    @PutMapping("/{id}")
    @Operation(summary = "Update a customer's profile - your own, or any if ADMIN")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "404", description = "No such customer")
    @ApiResponse(responseCode = "409", description = "Customer is inactive, or was modified concurrently")
    public CustomerResponse updateCustomer(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.updateCustomer(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a customer - your own, or any if ADMIN")
    public void deactivateCustomer(@PathVariable UUID id) {
        customerService.deactivateCustomer(id);
    }
}
