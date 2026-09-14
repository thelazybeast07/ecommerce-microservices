package com.ecommerce.user.controller;

import com.ecommerce.user.dto.CustomerResponse;
import com.ecommerce.user.entity.CustomerStatus;
import com.ecommerce.user.exception.ResourceNotFoundException;
import com.ecommerce.user.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

/**
 * Web-layer slice test: starts only Spring MVC (controllers, advice, JSON), not JPA or the
 * database. Verifies the HTTP contract: status codes, headers, JSON shape, validation.
 */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void register_validRequest_returns201WithLocationAndNoPasswordInBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(customerService.register(any())).thenReturn(new CustomerResponse(
                id, "Jane", "Doe", "jane.doe@example.com", "+14165550123",
                CustomerStatus.ACTIVE, Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Jane","lastName":"Doe","email":"jane.doe@example.com",
                                 "phone":"+14165550123","password":"correct-horse-battery"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/customers/" + id)))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_invalidRequest_returns400WithEveryFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"","lastName":"Doe","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'firstName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());

        verifyNoInteractions(customerService);
    }

    @Test
    void getCustomer_unknownId_returns404ProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(customerService.getCustomer(id)).thenThrow(ResourceNotFoundException.of("Customer", id));

        mockMvc.perform(get("/api/v1/customers/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Customer with id '" + id + "' was not found"));
    }

    @Test
    void getCustomer_malformedId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerService);
    }
}
