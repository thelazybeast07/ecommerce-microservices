package com.ecommerce.user.service;

import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.TokenResponse;
import com.ecommerce.user.entity.Customer;
import com.ecommerce.user.exception.InvalidCredentialsException;
import com.ecommerce.user.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Login. Kept separate from {@link CustomerService} because it is a different concern:
 * CustomerService manages profiles, this one proves identity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Verifies an email and password and issues a token.
     *
     * <p>Every failure path returns the SAME message on purpose. If "no such email" read
     * differently from "wrong password", anyone could probe which addresses are registered -
     * a privacy leak and the first step of a targeted attack. This is called user enumeration.
     */
    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> {
                    // The real reason goes in OUR log; the caller gets the generic message.
                    log.info("Login failed: no account for the supplied email");
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
            log.info("Login failed: wrong password for customerId={}", customer.getId());
            throw new InvalidCredentialsException();
        }

        if (!customer.isActive()) {
            log.info("Login refused: customerId={} is inactive", customer.getId());
            throw new InvalidCredentialsException();
        }

        log.info("Login succeeded for customerId={}", customer.getId());
        return new TokenResponse(jwtService.issueToken(customer), "Bearer", jwtService.expirySeconds());
    }
}
