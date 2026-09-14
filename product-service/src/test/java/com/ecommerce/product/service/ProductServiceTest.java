package com.ecommerce.product.service;

import com.ecommerce.product.dto.CreateProductRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private ProductService productService;

    private final UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository, new ProductMapper());
    }

    @Test
    @DisplayName("createProduct normalises the SKU and saves an ACTIVE product")
    void createProduct_success() {
        when(productRepository.existsBySku("TSHIRT-BLK-M")).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory()));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        ProductResponse response = productService.createProduct(
                new CreateProductRequest("  tshirt-blk-m  ", "Black tee", null,
                        new BigDecimal("24.99"), "CAD", categoryId));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSku()).isEqualTo("TSHIRT-BLK-M");
        assertThat(captor.getValue().getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(response.categoryName()).isEqualTo("Footwear");
    }

    @Test
    void createProduct_duplicateSku_isRejectedBeforeTouchingTheCategory() {
        when(productRepository.existsBySku("TSHIRT-BLK-M")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(
                new CreateProductRequest("TSHIRT-BLK-M", "Black tee", null,
                        new BigDecimal("24.99"), "CAD", categoryId)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("a missing category is 422, not 404: the body is unusable, the URL is fine")
    void createProduct_missingCategory_isUnprocessable() {
        when(productRepository.existsBySku("TSHIRT-BLK-M")).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(
                new CreateProductRequest("TSHIRT-BLK-M", "Black tee", null,
                        new BigDecimal("24.99"), "CAD", categoryId)))
                .isInstanceOf(UnprocessableRequestException.class);
    }

    @Test
    void createProduct_inactiveCategory_isUnprocessable() {
        Category inactive = activeCategory();
        inactive.deactivate();
        when(productRepository.existsBySku("TSHIRT-BLK-M")).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> productService.createProduct(
                new CreateProductRequest("TSHIRT-BLK-M", "Black tee", null,
                        new BigDecimal("24.99"), "CAD", categoryId)))
                .isInstanceOf(UnprocessableRequestException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void getProduct_notFound() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void searchProducts_invertedPriceRange_isUnprocessable() {
        assertThatThrownBy(() -> productService.searchProducts(null, null, null,
                new BigDecimal("50"), new BigDecimal("10"), PageRequest.of(0, 20)))
                .isInstanceOf(UnprocessableRequestException.class);
    }

    @Test
    void updateProduct_inactiveProduct_isRejected() {
        UUID id = UUID.randomUUID();
        Product product = productWithId(id);
        product.deactivate();
        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.updateProduct(id,
                new UpdateProductRequest("New name", null, new BigDecimal("26.99"), "CAD", categoryId)))
                .isInstanceOf(InvalidResourceStateException.class);

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void deactivateProduct_isIdempotent() {
        UUID id = UUID.randomUUID();
        Product product = productWithId(id);
        product.deactivate();
        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        productService.deactivateProduct(id);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void lookupProducts_emptyInput_returnsEmptyWithoutQuerying() {
        assertThat(productService.lookupProducts(List.of())).isEmpty();
        verify(productRepository, never()).findAllByIdIn(any());
    }

    @Test
    void lookupProducts_tooManyIds_isUnprocessable() {
        List<UUID> tooMany = IntStream.rangeClosed(0, ProductService.MAX_LOOKUP_IDS)
                .mapToObj(i -> UUID.randomUUID())
                .toList();

        assertThatThrownBy(() -> productService.lookupProducts(tooMany))
                .isInstanceOf(UnprocessableRequestException.class);
    }

    @Test
    @DisplayName("lookupProducts returns only what exists; the caller decides how to report gaps")
    void lookupProducts_returnsFoundOnly() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(productRepository.findAllByIdIn(List.of(known, unknown)))
                .thenReturn(List.of(productWithId(known)));

        List<ProductLookupResponse> found = productService.lookupProducts(List.of(known, unknown));

        assertThat(found).singleElement()
                .satisfies(p -> assertThat(p.id()).isEqualTo(known));
    }

    private Category activeCategory() {
        Category category = new Category("Footwear", "Shoes and boots");
        ReflectionTestUtils.setField(category, "id", categoryId);
        return category;
    }

    private Product productWithId(UUID id) {
        Product product = new Product("TSHIRT-BLK-M", "Black tee", null,
                new BigDecimal("24.99"), "CAD", activeCategory());
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
