package com.wayfare.commons.web;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Consistent pagination envelope across all services (design §5.3:
 * {@code ?page=&size=&sort=}). Wrap any Spring Data {@link Page} with
 * {@link #from(Page)}.
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
                page.isLast()
        );
    }
}
