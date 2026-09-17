package com.ecommerce.user.service;

import com.ecommerce.user.configuration.JwtProperties;
import com.ecommerce.user.entity.Customer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static com.ecommerce.user.service.CustomerServiceTest.customerWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for token creation. No Spring, no database.
 *
 * <p>These pin down the three properties everything else depends on: the token carries the
 * right facts, a tampered token is rejected, and an expired token is rejected.
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256!";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, 15, "ecommerce-user-service"));

    @Test
    @DisplayName("the token carries the customer id as subject and the role as a claim")
    void issueToken_carriesIdAndRole() {
        UUID id = UUID.randomUUID();

        Claims claims = parse(jwtService.issueToken(customerWithId(id)), SECRET);

        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.get(JwtService.CLAIM_ROLE)).isEqualTo("CUSTOMER");
        assertThat(claims.getIssuer()).isEqualTo("ecommerce-user-service");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("the payload is readable without any key - which is why no secret may go in it")
    void payloadIsOnlyEncoded_notEncrypted() {
        String token = jwtService.issueToken(customerWithId(UUID.randomUUID()));

        String payload = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat(payload).contains("CUSTOMER");
        // Reading it is fine and unavoidable. What matters is that it cannot be CHANGED.
    }

    @Test
    @DisplayName("a tampered payload fails verification")
    void tamperedToken_isRejected() {
        String token = jwtService.issueToken(customerWithId(UUID.randomUUID()));
        String[] parts = token.split("\\.");

        // Swap the role to ADMIN but keep the original signature
        String forgedPayload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("CUSTOMER", "ADMIN___");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThatThrownBy(() -> parse(forged, SECRET)).isInstanceOf(SignatureException.class);
    }

    @Test
    void tokenSignedWithADifferentSecret_isRejected() {
        String token = jwtService.issueToken(customerWithId(UUID.randomUUID()));

        assertThatThrownBy(() -> parse(token, "a-completely-different-secret-key-value!!"))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void expiredToken_isRejected() {
        // A negative expiry issues a token that expired a minute ago
        JwtService expired = new JwtService(new JwtProperties(SECRET, -1, "ecommerce-user-service"));

        assertThatThrownBy(() -> parse(expired.issueToken(customerWithId(UUID.randomUUID())), SECRET))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void secretShorterThan256Bits_failsFastAtStartup() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 15, "issuer")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    private static Claims parse(String token, String secret) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
