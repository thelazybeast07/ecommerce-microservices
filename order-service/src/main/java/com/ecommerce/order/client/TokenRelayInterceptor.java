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
