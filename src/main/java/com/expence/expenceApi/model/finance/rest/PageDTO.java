package com.expence.expenceApi.model.finance.rest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Our own paging envelope rather than serialising Spring Data's {@code Page}, whose JSON
 * shape is unstable across versions (it carries the whole {@code Pageable} and {@code Sort}
 * and Boot 3.3 deprecated serialising it directly). The UI reads these five fields.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageDTO<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <E, D> PageDTO<D> of(Page<E> page, Function<E, D> mapper) {
        return PageDTO.<D>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
