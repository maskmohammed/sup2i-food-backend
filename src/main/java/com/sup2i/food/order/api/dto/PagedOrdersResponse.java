package com.sup2i.food.order.api.dto;

import java.util.List;

public record PagedOrdersResponse(
    List<OrderResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}