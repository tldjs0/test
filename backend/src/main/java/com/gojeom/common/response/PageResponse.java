package com.gojeom.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지네이션 목록 응답. (API.md §1)
 *
 * <pre>
 * { "items": [...], "page": { "number": 0, "size": 20, "totalElements": 37, "totalPages": 2 } }
 * </pre>
 */
public record PageResponse<T>(List<T> items, PageInfo page) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }

    public record PageInfo(int number, int size, long totalElements, int totalPages) {
    }
}
