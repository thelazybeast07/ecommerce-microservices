package com.ecommerce.user.integration;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through every layer (HTTP -> controller -> service -> JPA -> PostgreSQL).
 * Few of these, covering the flows that matter most; the unit tests cover the edge cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CustomerApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void customerLifecycle_registerAddAddressProfileDeactivate() throws Exception {
        String email = uniqueEmail();

        String body = mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mockMvc.perform(post("/api/v1/customers/{id}/addresses", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addressLine1":"100 King St W","city":"Toronto","state":"ON",
                                 "postalCode":"M5X 1A9","country":"CA","addressType":"SHIPPING"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/customers/{id}/profile", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.email").value(email))
                .andExpect(jsonPath("$.addresses.length()").value(1));

        mockMvc.perform(delete("/api/v1/customers/{id}", id))
                .andExpect(status().isNoContent());

        // Inactive customers are read-only
        mockMvc.perform(put("/api/v1/customers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Janet","lastName":"Doe"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void registeringTheSameEmailTwice_returns409_evenWithDifferentCase() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(registerJson(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email.toUpperCase())))
                .andExpect(status().isConflict());
    }

    @Test
    void listCustomers_withUnknownSortProperty_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/customers").param("sort", "doesNotExist,asc"))
                .andExpect(status().isBadRequest());
    }

    private static String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    private static String registerJson(String email) {
        return """
                {"firstName":"Jane","lastName":"Doe","email":"%s","password":"correct-horse-battery"}
                """.formatted(email);
    }
}
