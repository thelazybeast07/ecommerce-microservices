package com.ecommerce.order.client;

import com.ecommerce.order.client.dto.AddressDto;
import com.ecommerce.order.client.dto.CustomerDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Declarative HTTP client for user-service.
 *
 * <p>There is no implementation to write: at startup Feign generates one from these
 * annotations. A call to {@link #getCustomer} becomes
 * {@code GET http://localhost:8081/api/v1/customers/{id}} and the JSON response is
 * deserialised into {@link CustomerDto}.
 *
 * <p>{@code url} comes from configuration ({@code services.user.url}), so pointing at a
 * different environment is a config change, not a code change.
 */
@FeignClient(name = "user-service", url = "${services.user.url}",
        configuration = FeignClientConfig.class)
public interface UserServiceClient {

    @GetMapping("/api/v1/customers/{customerId}")
    CustomerDto getCustomer(@PathVariable UUID customerId);

    @GetMapping("/api/v1/customers/{customerId}/addresses/{addressId}")
    AddressDto getAddress(@PathVariable UUID customerId, @PathVariable UUID addressId);
}
