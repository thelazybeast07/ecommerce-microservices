package com.ecommerce.user.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination envelope. Spring's {@code PageImpl} is not returned directly because its
 * JSON shape is an implementation detail that can change between Spring Data versions; this
 * record is our API contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
