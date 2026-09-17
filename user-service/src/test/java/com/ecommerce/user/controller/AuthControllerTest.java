package com.ecommerce.user.controller;

import com.ecommerce.user.dto.TokenResponse;
import com.ecommerce.user.exception.InvalidCredentialsException;
import com.ecommerce.user.configuration.JwtAuthenticationFilter;
import com.ecommerce.user.configuration.SecurityConfig;
import com.ecommerce.user.configuration.SecurityProblemHandlers;
import com.ecommerce.user.service.AuthService;
import com.ecommerce.user.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityProblemHandlers.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        when(authService.login(any())).thenReturn(new TokenResponse("a.b.c", "Bearer", 900));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane.doe@example.com","password":"correct-horse-battery"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a.b.c"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void login_badCredentials_returns401_notA403() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane.doe@example.com","password":"wrong"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication failed"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void login_missingPassword_returns400_withoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jane.doe@example.com"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());

        verifyNoInteractions(authService);
    }
}
