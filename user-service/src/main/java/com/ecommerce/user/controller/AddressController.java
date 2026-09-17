package com.ecommerce.user.controller;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Addresses are a sub-resource of a customer: they cannot exist without one, and the URL makes
 * that ownership explicit. Not paginated, because a customer has a handful of addresses.
 */
/**
 * Every method here is guarded by the same rule, so it is declared once on the class:
 * you may manage your own addresses, and an admin may manage anyone's.
 */
@PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal")
@RestController
@RequestMapping("/api/v1/customers/{customerId}/addresses")
@RequiredArgsConstructor
@Tag(name = "Customer addresses", description = "Manage a customer's addresses")
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    @Operation(summary = "Add an address to a customer")
    public ResponseEntity<AddressResponse> addAddress(@PathVariable UUID customerId,
                                                      @Valid @RequestBody AddressRequest request) {
        AddressResponse created = addressService.addAddress(customerId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{addressId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List a customer's addresses")
    public List<AddressResponse> getAddresses(@PathVariable UUID customerId) {
        return addressService.getAddresses(customerId);
    }

    @GetMapping("/{addressId}")
    @Operation(summary = "Get one address", description = "Also used by order-service to snapshot the shipping address")
    public AddressResponse getAddress(@PathVariable UUID customerId, @PathVariable UUID addressId) {
        return addressService.getAddress(customerId, addressId);
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Replace an address")
    public AddressResponse updateAddress(@PathVariable UUID customerId,
                                         @PathVariable UUID addressId,
                                         @Valid @RequestBody AddressRequest request) {
        return addressService.updateAddress(customerId, addressId, request);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an address")
    public void deleteAddress(@PathVariable UUID customerId, @PathVariable UUID addressId) {
        addressService.deleteAddress(customerId, addressId);
    }
}
