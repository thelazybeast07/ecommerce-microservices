package com.ecommerce.user.service;

import com.ecommerce.user.configuration.JwtProperties;
import com.ecommerce.user.entity.Customer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Builds signed JSON Web Tokens.
 *
 * <p>A JWT has three dot-separated parts: header, payload and signature. The payload is only
 * BASE64-ENCODED, never encrypted - anyone holding the token can read it. What they cannot do
 * is CHANGE it: altering one character breaks the signature, and producing a valid replacement
 * requires the secret key.
 *
 * <p>The rule that follows: never put anything secret in a token. Ids, roles and timestamps are
 * fine. Passwords, card numbers and personal details are not.
 */
@Service
public class JwtService {

    /** Our own claim for the caller's role. "sub" and "exp" are standard; this one is not. */
    public static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        // HS256 needs at least 256 bits. Failing at startup beats discovering a too-short
        // key on the first login attempt after deployment.
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 characters (256 bits) for HS256; got " + keyBytes.length);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Issues a token for a customer.
     *
     * <p>The subject is the customer's UUID rather than their email. Emails change; the id does
     * not, and it is the id that order-service and product-service already know this person by.
     */
    public String issueToken(Customer customer) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(properties.expiryMinutes()));

        return Jwts.builder()
                .subject(customer.getId().toString())
                .claim(CLAIM_ROLE, customer.getRole().name())
                .issuer(properties.issuer())
                // A unique id per token. Unused today, but it is what a revocation denylist
                // would key on once Redis arrives.
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public long expirySeconds() {
        return Duration.ofMinutes(properties.expiryMinutes()).toSeconds();
    }

    /**
     * Verifies a token and returns what it says.
     *
     * <p>This is a purely LOCAL operation: signature check, expiry check, issuer check, all
     * arithmetic on bytes we already hold. No database query, no call to another service. That
     * is the whole reason a token beats a session in a multi-service system.
     *
     * @throws io.jsonwebtoken.JwtException if the signature is wrong, the token has expired,
     *                                      the issuer does not match, or it is malformed
     */
    public TokenPayload parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                // Reject a token minted by something else, even if it were somehow signed
                // with our key. Checking the issuer costs nothing and closes a door.
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new TokenPayload(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_ROLE, String.class));
    }

    /**
     * The facts carried by a verified token.
     *
     * @param userId the caller's customer id, taken from the standard "sub" claim
     * @param role   the caller's role, as a plain string - the filter turns it into an authority
     */
    public record TokenPayload(UUID userId, String role) {
    }
}
