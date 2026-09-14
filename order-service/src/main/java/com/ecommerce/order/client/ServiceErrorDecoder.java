package com.ecommerce.order.client;

import com.ecommerce.order.exception.DownstreamServiceException;
import com.ecommerce.order.exception.DownstreamNotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * Translates a downstream HTTP failure into one of our own exceptions, at the boundary.
 *
 * <p>This matters: without it, a 404 from user-service surfaces as a raw {@code FeignException}
 * and, unhandled, becomes a 500 from order-service - telling the caller "I am broken" when the
 * truth is "you referenced a customer that does not exist". Callers of order-service should
 * never see Feign types at all.
 */
@Slf4j
public class ServiceErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        log.warn("Downstream call {} returned HTTP {}", methodKey, status);

        // 404: the referenced resource does not exist. The service itself is healthy.
        if (status == 404) {
            return new DownstreamNotFoundException(
                    "Downstream resource not found (%s)".formatted(methodKey));
        }
        // 5xx: the downstream service is broken or unavailable -> we report 503, not 500,
        // because OUR service is fine; a dependency is not.
        if (status >= 500) {
            return new DownstreamServiceException(
                    "Downstream service error calling %s (HTTP %d)".formatted(methodKey, status));
        }
        // Anything else (4xx) means we sent a bad request - a bug on our side.
        return defaultDecoder.decode(methodKey, response);
    }
}
