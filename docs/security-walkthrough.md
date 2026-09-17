# Phase 2: Security — Code Walkthrough

How authentication and authorization are implemented across the three services, with the
actual source code and the reasoning behind each piece.

Read this after `phase-1-design.md`. The order below follows a request: first how a token is
created, then how it is verified, then how the rules use it, then how it travels between
services.

---

## 1. The model in one paragraph

user-service is the only issuer of identity. A customer proves who they are once, at
`POST /api/v1/auth/login`, and receives a signed JWT carrying their id and role. Every service
verifies that token locally — signature, expiry, issuer — with no database query and no call to
user-service. Authorization is then layered on top: path rules decide which endpoints need a
token at all, and method rules decide who may call each one. When order-service calls the other
services, it forwards the caller's own token, so identity survives the whole chain.

## 2. Storing the role — the V3 migration

```sql
-- Authentication needs to know WHAT a caller may do, not just who they are.
--
-- The DEFAULT matters: customers already exist in this table, and a NOT NULL column
-- cannot be added to a populated table without telling PostgreSQL what to put in the
-- existing rows. Every current customer becomes a CUSTOMER, which is correct.
ALTER TABLE customers
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_role CHECK (role IN ('CUSTOMER', 'ADMIN'));
```

The `DEFAULT` is load-bearing: the table already had rows, and PostgreSQL will not add a
`NOT NULL` column to a populated table without being told what goes in the existing ones.

## 3. Issuing a token — JwtService (user-service)

```java
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
```

Points worth pausing on:

- **The subject is the customer's UUID, not their email.** Emails change; the id does not, and
  it is the same id every other service already knows this person by.
- **The payload is encoded, not encrypted.** Anyone holding a token can read it — paste one
  into jwt.io. Safety comes from the signature: change one character and verification fails.
  The rule that follows: nothing secret ever goes in a token.
- **The 32-character minimum is enforced at startup**, because discovering a weak key on the
  first login attempt after deployment is the worst possible time.
- **`parseToken` is entirely local.** No I/O of any kind. This is the property the whole
  architecture rests on: any service can authenticate any caller while every other service is
  down.

## 4. The login flow — AuthService (user-service)

```java
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
```

The deliberate design here is the *indistinguishable failure*: unknown email, wrong password
and deactivated account all produce the same 401 with the same message. If they differed, the
login endpoint would double as a directory of which emails have accounts — the attack is
called user enumeration. The real reason goes into our own log, where it belongs.

## 5. Identifying the caller — JwtAuthenticationFilter

```java
package com.ecommerce.user.configuration;

import com.ecommerce.user.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the bearer token on every request and records who the caller is.
 *
 * <p>Extends {@link OncePerRequestFilter} rather than implementing Filter directly: a single
 * HTTP request can pass through the filter chain more than once internally (forwards, error
 * dispatches), and this base class guarantees the body runs exactly once per request.
 *
 * <p>Note what this filter does NOT do: it never rejects anything. If the token is missing or
 * bad, it simply leaves the request unauthenticated and passes it along. Deciding whether an
 * unauthenticated request is acceptable belongs to {@link SecurityConfig} - some endpoints
 * (login, registration) are fine without one. Keeping "identify" separate from "authorize"
 * is what lets one filter serve both public and protected endpoints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        // Already authenticated on this request? Leave it alone.
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.TokenPayload payload = jwtService.parseToken(token);

                // Spring Security expects roles to be prefixed with "ROLE_". That prefix is a
                // convention its @PreAuthorize("hasRole('ADMIN')") support relies on, so we add
                // it here and keep it out of our own enum.
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + payload.role()));

                var authentication = new UsernamePasswordAuthenticationToken(
                        payload.userId(),   // the principal: the caller's UUID
                        null,               // no credentials; the token already proved it
                        authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // From here on, anything in this request can ask who the caller is.
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException | IllegalArgumentException ex) {
                // Expired, tampered, wrong issuer, malformed. Log the reason for us, tell the
                // caller nothing: a precise message would help someone probe the token format.
                log.debug("Rejecting token: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Pulls the token out of "Authorization: Bearer &lt;token&gt;", or null if absent. */
    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String value = header.substring(PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}
```

The filter's restraint is the point: it **never rejects a request**. Missing token, expired
token, garbage token — it simply leaves the request unauthenticated and passes it on. Whether
an unauthenticated request is acceptable is a different question, answered by SecurityConfig.
Keeping "identify" and "authorize" separate is what lets the same filter serve login (public)
and customer records (protected) without special cases.

## 6. Path rules — SecurityConfig (user-service)

```java
package com.ecommerce.user.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Decides which requests need a token.
 *
 * <p>{@code @EnableMethodSecurity} switches on {@code @PreAuthorize} so individual methods can
 * add finer rules ("only an admin", "only your own record") on top of the path rules here.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityProblemHandlers problemHandlers;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF protects browser form posts that rely on cookies. We use no cookies and no
            // sessions - the token travels in a header that a malicious site cannot make the
            // browser attach - so the protection has nothing to protect and only breaks clients.
            .csrf(csrf -> csrf.disable())

            // STATELESS: never create an HttpSession. Every request must carry its own proof.
            // This is what allows several copies of this service to run with no shared session
            // store - any copy can serve any request.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // --- public: you cannot require a token to obtain a token ---
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                // --- public: a new customer has no account yet ---
                .requestMatchers(HttpMethod.POST, "/api/v1/customers").permitAll()

                // --- public for local development only. In production these would be
                //     restricted to an internal network or removed entirely. ---
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Container and orchestrator probes call these with no credentials.
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()

                // --- everything else needs a valid token ---
                // DEFAULT DENY, and the order matters: this is the last rule, so any endpoint
                // added later is protected unless someone deliberately opens it. The opposite
                // arrangement ships new endpoints wide open, silently.
                .anyRequest().authenticated())

            // Our filter runs before Spring's username/password filter so that by the time
            // authorization is evaluated, the SecurityContext is already populated.
            // Replace Spring Security's default empty/HTML rejections with problem JSON,
            // so authentication failures look like every other error in the platform.
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(problemHandlers.authenticationEntryPoint())
                .accessDeniedHandler(problemHandlers.accessDeniedHandler()))

            // Our filter runs before Spring's username/password filter so that by the time
            // authorization is evaluated, the SecurityContext is already populated.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

Two decisions here matter more than the rest:

- **Default deny.** The last rule is `anyRequest().authenticated()`. Any endpoint added next
  month is protected before anyone remembers to think about it. The opposite arrangement —
  listing what to protect — ships forgotten endpoints wide open, silently.
- **Stateless.** No HttpSession is ever created. Every request stands alone, which is what
  allows several instances of a service to run behind a load balancer with no shared session
  store.

product-service's config differs in one interesting way — catalogue reads are public, and the
rule ordering is significant:

```java
.authorizeHttpRequests(auth -> auth
                // --- public: browsing the shop needs no account ---
                // NOTE the ordering: /products/lookup is listed BEFORE /products/** so the
                // more specific rule wins. Rules are evaluated top to bottom, first match
                // applies, so a broad pattern placed first would swallow everything under it.
                .requestMatchers(HttpMethod.GET, "/api/v1/products/lookup").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/**").permitAll()

                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()

                // --- everything else: default deny. Writes are additionally ADMIN-only,
                //     enforced by @PreAuthorize on the controller methods. ---
                .anyRequest().authenticated())
```

`/products/lookup` is matched **before** `/products/**`. Rules are evaluated top to bottom and
the first match wins; swap those two lines and the lookup endpoint silently becomes public.

## 7. Making rejections consistent — SecurityProblemHandlers

```java
package com.ecommerce.user.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Makes Spring Security's own rejections look like every other error in the platform.
 *
 * <p>Without this, a request with no token gets Spring Security's default response: an empty
 * body, or an HTML login page. Every other failure in this service returns RFC 9457 problem
 * JSON, and a client should not have to special-case authentication.
 *
 * <p>These two run when the request is rejected by the FILTER CHAIN, before any controller is
 * reached. Rejections raised by {@code @PreAuthorize} happen later, inside the controller call,
 * and are handled by GlobalExceptionHandler instead.
 */
@Component
@RequiredArgsConstructor
public class SecurityProblemHandlers {

    private final ObjectMapper objectMapper;

    /** No credentials, or credentials we could not accept -> 401. */
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> write(response, HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "A valid bearer token is required. Obtain one from POST /api/v1/auth/login.");
    }

    /** We know who you are; you still may not do this -> 403. */
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, HttpStatus.FORBIDDEN,
                "Access denied",
                "You do not have permission to perform this action.");
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
```

## 8. Method rules — the three techniques

**Technique 1: `@PreAuthorize`** — when the rule only needs the URL or body, available before
the method runs.

```java
// CustomerController — listing everyone is staff work
@PreAuthorize("hasRole('ADMIN')")
@GetMapping

// CustomerController — your own record, or any if admin. #id is the path variable;
// authentication.principal is the UUID the filter took from the token.
@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal")
@GetMapping("/{id}")

// OrderController — you may only place orders as yourself
@PreAuthorize("hasRole('ADMIN') or #request.customerId() == authentication.principal")
@PostMapping
```

The ownership comparison is the guard against **IDOR** (insecure direct object reference) —
reading someone else's data by editing an id in the URL. It is consistently the most common
real-world API vulnerability.

**Technique 2: `@PostAuthorize`** — when the answer lives in the database.

```java
// OrderController — who owns an order is not in the URL. Load it, then check.
@PostAuthorize("hasRole('ADMIN') or returnObject.customerId() == authentication.principal")
@GetMapping("/{id}")
```

Safe **only because this is a read**: if the rule fails, the loaded order is discarded and a
403 returned. Nothing happened that needs undoing.

**Technique 3: an explicit check in the service** — when the answer lives in the database AND
the method changes data.

```java
@Transactional
    public OrderResponse cancelOrder(UUID id, UUID callerId, boolean callerIsAdmin) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));

        // Checked here, not with an annotation: the owner is only known once the order is
        // loaded, and this must happen BEFORE anything is changed.
        if (!callerIsAdmin && !order.getCustomerId().equals(callerId)) {
            throw new AccessDeniedException("This order belongs to another customer");
        }
```

`@PostAuthorize` here would be a real bug, not a style choice: the cancel would happen, and
only then would the caller be told no. The check must run after loading but before mutating —
which is a place only the service can reach.

## 9. Token propagation — TokenRelayInterceptor (order-service)

```java
package com.ecommerce.order.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Copies the caller's bearer token onto every outgoing Feign call.
 *
 * <p>Without this, order-service would be an anonymous caller the moment it tries to fetch a
 * customer or an address, and user-service would answer 401 - so placing an order would fail
 * even though the customer is perfectly entitled to place it.
 *
 * <p>This is TOKEN PROPAGATION: the customer's own identity travels the whole chain, so
 * user-service sees "Jane asking about Jane" and its existing ownership rule passes with no
 * special case for service callers.
 *
 * <p>The trade-off, stated plainly: a stolen token works against every service, not just the
 * one it was presented to. The alternative is giving each service its own machine identity with
 * narrow permissions, which is what larger systems do and what this would grow into.
 *
 * <p>{@link RequestContextHolder} reads the HTTP request currently being served on this thread.
 * That works because Feign calls here happen on the same thread as the incoming request. If this
 * service later moves to async or reactive calls, the context would not follow and this class
 * would need rethinking - a real and commonly-hit limitation.
 */
@Component
public class TokenRelayInterceptor implements RequestInterceptor {

    private static final String HEADER = "Authorization";

    @Override
    public void apply(RequestTemplate template) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return; // No inbound request (e.g. a scheduled task) - nothing to forward
        }

        String authorization = request.getHeader(HEADER);
        if (authorization != null && !authorization.isBlank()) {
            template.header(HEADER, authorization);
        }
    }

    private static HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return (attributes instanceof ServletRequestAttributes servletAttributes)
                ? servletAttributes.getRequest()
                : null;
    }
}
```

Registered once in `FeignClientConfig`, so every outgoing call gets the header without any
call-site code. The consequence: when order-service fetches the customer's address,
user-service sees *the customer* asking about their own address, and the ownership rule from
section 8 passes with no special case for service callers.

The stated trade-off: a leaked token now works against every service. The upgrade path is
scoped service identities or OAuth2 token exchange — machinery this project does not need yet,
but worth being able to name.

## 10. When the forwarded token is refused — ServiceErrorDecoder

```java
package com.ecommerce.order.exception;

/**
 * A downstream service refused the forwarded token (401 or 403).
 *
 * <p>Mapped to 401, not 500: this service is working correctly - the caller's credentials were
 * not accepted further down the chain, most often because the token expired mid-request.
 */
public class DownstreamAuthException extends RuntimeException {

    public DownstreamAuthException(String message) {
        super(message);
    }
}
```

A downstream 401 most commonly means the token expired between login and the call. Reporting
it as 500 would say "we are broken"; reporting 503 would say "try again later". Both are
wrong — the caller needs to sign in again, so 401 it is, with a message that says exactly that.

## 11. The bug the exception handlers had to avoid

`@PreAuthorize` throws `AccessDeniedException` **inside** the controller call. Every service
already had a catch-all:

```java
@ExceptionHandler(Exception.class)   // would have caught it
```

Without an explicit handler placed above it, a permission failure would surface as **500
Internal Server Error** — misleading to the caller and hiding a security signal in the logs.
Each service therefore carries:

```java
@ExceptionHandler(AccessDeniedException.class)
public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return problem(HttpStatus.FORBIDDEN, "Access denied",
            "You do not have permission to perform this action.");
}
```

`SecurityRulesTest` fails if this handler is removed.

## 12. What is deliberately imperfect

Honesty section. These are known, accepted, and documented rather than hidden:

- **HS256's shared secret** means any service able to verify a token can also mint one. A
  compromised product-service could issue itself an admin token. Fix: RS256 — user-service
  signs with a private key, everyone else verifies with the public one.
- **A token cannot be revoked.** Deactivate a customer and their existing token works until it
  expires. Mitigations, in increasing order of machinery: short expiry (done — 15 minutes),
  refresh tokens, a Redis denylist keyed on the token's `jti`.
- **No rate limiting on login.** Nothing slows a password-guessing loop yet.
- **Swagger and the H2-style conveniences are public** — acceptable on a laptop, not in
  production.
