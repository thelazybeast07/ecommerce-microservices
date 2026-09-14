package com.ecommerce.order.client;

import com.ecommerce.order.client.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "product-service", url = "${services.product.url}",
        configuration = FeignClientConfig.class)
public interface ProductServiceClient {

    /**
     * ONE call for the whole cart. Calling a single-product endpoint per line item would be the
     * N+1 problem over the network: 10 items, 10 round trips, 10 chances to fail.
     */
    @GetMapping("/api/v1/products/lookup")
    List<ProductDto> lookupProducts(@RequestParam("ids") List<UUID> ids);
}
