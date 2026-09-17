package com.ecommerce.product.configuration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Verifies tokens. This service NEVER creates one - there is no issue method here at all,
 * which is the point: the catalogue has no business minting identities.
 *
 * <p>Verification is entirely local: signature, expiry and issuer are all checked against bytes
 * this service already holds. No database query, no call to user-service. That is exactly why
 * a token beats a session across several services - product-service can authenticate a caller
 * while user-service is switched off.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 characters (256 bits); got " + keyBytes.length);
        }
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * @throws io.jsonwebtoken.JwtException if the signature is wrong, the token has expired,
     *                                      the issuer does not match, or it is malformed
     */
    public TokenPayload parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new TokenPayload(UUID.fromString(claims.getSubject()), claims.get(CLAIM_ROLE, String.class));
    }

    public record TokenPayload(UUID userId, String role) {
    }
}
