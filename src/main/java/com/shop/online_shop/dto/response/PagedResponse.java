package com.shop.online_shop.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** جایگزین Page خام Spring — ساختار تمیزتر برای کلاینت */
public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
    public static <E, T> PagedResponse<T> from(Page<E> source, Function<E, T> mapper) {
        return new PagedResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast()
        );
    }
}