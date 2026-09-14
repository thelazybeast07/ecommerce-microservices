package com.ecommerce.product.service;

import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.PageResponse;
import com.ecommerce.product.dto.ProductLookupResponse;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.dto.UpdateProductRequest;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.entity.ProductStatus;
import com.ecommerce.product.exception.DuplicateResourceException;
import com.ecommerce.product.exception.InvalidResourceStateException;
import com.ecommerce.product.exception.ResourceNotFoundException;
import com.ecommerce.product.exception.UnprocessableRequestException;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.repository.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    /** Guards the batch lookup so one request cannot ask for an unbounded number of products. */
    public static final int MAX_LOOKUP_IDS = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        String sku = normalizeSku(request.sku());
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateResourceException("A product with SKU '%s' already exists".formatted(sku));
        }

        Category category = resolveActiveCategory(request.categoryId());
        Product product = productMapper.toEntity(request, sku, category);
        Product saved = productRepository.save(product);

        log.info("Created product id={} sku={}", saved.getId(), saved.getSku());
        return productMapper.toResponse(saved);
    }

    public ProductResponse getProduct(UUID id) {
        return productMapper.toResponse(findProduct(id));
    }

    /**
     * Search and filter. Every parameter is optional; {@link ProductSpecifications} builds the
     * WHERE clause from whichever ones were supplied.
     */
    public PageResponse<ProductResponse> searchProducts(String keyword, UUID categoryId, ProductStatus status,
                                                        BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new UnprocessableRequestException("minPrice must not be greater than maxPrice");
        }
        Page<Product> page = productRepository.findAll(
                ProductSpecifications.search(keyword, categoryId, status, minPrice, maxPrice), pageable);
        return PageResponse.from(page.map(productMapper::toResponse));
    }

    /**
     * Batch lookup for order-service. Returns only the products that exist; the caller compares
     * what came back against what it asked for and decides how to report the difference.
     */
    public List<ProductLookupResponse> lookupProducts(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_LOOKUP_IDS) {
            throw new UnprocessableRequestException(
                    "At most %d ids may be looked up at once".formatted(MAX_LOOKUP_IDS));
        }
        return productRepository.findAllByIdIn(ids).stream()
                .map(productMapper::toLookupResponse)
                .toList();
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = findProduct(id);
        if (!product.isActive()) {
            throw new InvalidResourceStateException(
                    "Product '%s' is inactive and cannot be modified".formatted(id));
        }

        Category category = resolveActiveCategory(request.categoryId());
        product.updateDetails(request.name(), request.description(), request.price(),
                request.currency(), category);

        return productMapper.toResponse(productRepository.saveAndFlush(product));
    }

    /**
     * Soft delete, idempotent. Past orders reference this product, so the row stays; it simply
     * disappears from the active catalogue and can no longer be ordered.
     */
    @Transactional
    public void deactivateProduct(UUID id) {
        Product product = findProduct(id);
        if (product.isActive()) {
            product.deactivate();
            log.info("Deactivated product id={}", id);
        }
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }

    /**
     * A missing category is 422, not 404: the URL's resource (the product) is fine, it is the
     * referenced category in the body that cannot be used.
     */
    private Category resolveActiveCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new UnprocessableRequestException(
                        "Category '%s' does not exist".formatted(categoryId)));
        if (!category.isActive()) {
            throw new UnprocessableRequestException(
                    "Category '%s' is inactive and cannot be assigned to a product".formatted(categoryId));
        }
        return category;
    }

    /** SKUs are case-insensitive business identifiers; store them one way so uniqueness holds. */
    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }
}
