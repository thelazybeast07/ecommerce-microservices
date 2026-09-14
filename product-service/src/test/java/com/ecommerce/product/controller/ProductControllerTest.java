package com.ecommerce.product.controller;

import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.ProductStatus;
import com.ecommerce.product.exception.ResourceNotFoundException;
import com.ecommerce.product.exception.UnprocessableRequestException;
import com.ecommerce.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void createProduct_valid_returns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(productService.createProduct(any())).thenReturn(new ProductResponse(
                id, "TSHIRT-BLK-M", "Black tee", null, new BigDecimal("24.99"), "CAD",
                categoryId, "Apparel", ProductStatus.ACTIVE, Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"TSHIRT-BLK-M","name":"Black tee","price":24.99,
                                 "currency":"CAD","categoryId":"%s"}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/products/" + id)))
                .andExpect(jsonPath("$.sku").value("TSHIRT-BLK-M"))
                .andExpect(jsonPath("$.categoryName").value("Apparel"));
    }

    @Test
    void createProduct_invalidFields_returns400WithEveryError() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"lower case!","name":"","price":-5,"currency":"dollars",
                                 "categoryId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.field == 'sku')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'price')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'currency')]").exists());

        verifyNoInteractions(productService);
    }

    @Test
    void getProduct_unknownId_returns404ProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.getProduct(id)).thenThrow(ResourceNotFoundException.of("Product", id));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void lookup_tooManyIds_returns422() throws Exception {
        when(productService.lookupProducts(any()))
                .thenThrow(new UnprocessableRequestException("At most 100 ids may be looked up at once"));

        mockMvc.perform(get("/api/v1/products/lookup").param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Unprocessable request"));
    }

    @Test
    void lookup_pathIsNotParsedAsAUuid() throws Exception {
        when(productService.lookupProducts(any())).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/products/lookup").param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }
}
