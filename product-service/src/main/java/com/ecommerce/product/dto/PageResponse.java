package com.ecommerce.product.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination envelope, identical in shape to user-service's. Duplicated on purpose:
 * a shared "common" module between services would couple their release cycles.
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
