package com.ecommerce.user.controller;

import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.TokenResponse;
import com.ecommerce.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and token issuing")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Exchange email and password for a token",
            description = "Send the returned token on later requests as: Authorization: Bearer <token>")
    @ApiResponse(responseCode = "200", description = "Token issued")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
