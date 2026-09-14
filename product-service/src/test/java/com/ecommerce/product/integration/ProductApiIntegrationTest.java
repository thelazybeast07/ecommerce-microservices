package com.ecommerce.product.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void productLifecycle_createSearchLookupDeactivate() throws Exception {
        String categoryId = createCategory("Apparel-" + UUID.randomUUID());
        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String body = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"Black cotton tee","description":"180gsm",
                                 "price":24.99,"currency":"CAD","categoryId":"%s"}
                                """.formatted(sku, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String productId = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/v1/products")
                        .param("q", "cotton")
                        .param("categoryId", categoryId)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value(sku));

        mockMvc.perform(get("/api/v1/products/lookup").param("ids", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(24.99))
                .andExpect(jsonPath("$[0].currency").value("CAD"))
                // The trimmed lookup projection must not leak catalogue presentation fields
                .andExpect(jsonPath("$[0].description").doesNotExist());

        mockMvc.perform(delete("/api/v1/products/{id}", productId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void createProduct_inInactiveCategory_returns422() throws Exception {
        String categoryId = createCategory("Retired-" + UUID.randomUUID());
        mockMvc.perform(delete("/api/v1/categories/{id}", categoryId))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-INACTIVE-CAT","name":"Orphan","price":9.99,
                                 "currency":"CAD","categoryId":"%s"}
                                """.formatted(categoryId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void duplicateSku_returns409() throws Exception {
        String categoryId = createCategory("Dup-" + UUID.randomUUID());
        String sku = "SKU-DUPE-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String payload = """
                {"sku":"%s","name":"First","price":10.00,"currency":"CAD","categoryId":"%s"}
                """.formatted(sku, categoryId);

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
    }

    private String createCategory(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Integration test category"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
