package com.ecommerce.product.controller;

import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.PageResponse;
import com.ecommerce.product.dto.ProductLookupResponse;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.dto.UpdateProductRequest;
import com.ecommerce.product.entity.ProductStatus;
import com.ecommerce.product.service.ProductService;
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

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalogue, search and filtering")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Create a product")
    @ApiResponse(responseCode = "201", description = "Created; Location header points to it")
    @ApiResponse(responseCode = "409", description = "A product with this SKU already exists")
    @ApiResponse(responseCode = "422", description = "Category does not exist or is inactive")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.createProduct(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "Search and filter products (paged)",
            description = "All filters are optional and combine with AND. "
                    + "Example: ?q=shirt&categoryId=...&status=ACTIVE&minPrice=10&maxPrice=50&sort=price,asc")
    public PageResponse<ProductResponse> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return productService.searchProducts(q, categoryId, status, minPrice, maxPrice, pageable);
    }

    /**
     * Declared before {@code /{id}} so Spring does not try to parse "lookup" as a UUID.
     * (Spring MVC actually prefers the more specific literal path, but keeping the order
     * explicit avoids surprises for anyone reading the class top to bottom.)
     */
    @GetMapping("/lookup")
    @Operation(summary = "Batch lookup by ids",
            description = "Used by order-service to price a whole cart in one call. Max 100 ids. "
                    + "Ids that do not exist are simply absent from the response.")
    @ApiResponse(responseCode = "422", description = "More than 100 ids requested")
    public List<ProductLookupResponse> lookupProducts(@RequestParam List<UUID> ids) {
        return productService.lookupProducts(ids);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    public ProductResponse getProduct(@PathVariable UUID id) {
        return productService.getProduct(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product", description = "SKU cannot be changed; retire and recreate instead")
    @ApiResponse(responseCode = "409", description = "Product is inactive, or was modified concurrently")
    @ApiResponse(responseCode = "422", description = "Category does not exist or is inactive")
    public ProductResponse updateProduct(@PathVariable UUID id,
                                         @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a product (soft delete, idempotent)")
    public void deactivateProduct(@PathVariable UUID id) {
        productService.deactivateProduct(id);
    }
}
