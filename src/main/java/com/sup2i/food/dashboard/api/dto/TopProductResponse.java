package com.sup2i.food.dashboard.api.dto;

import java.util.UUID;

public record TopProductResponse(
    UUID productId,
    String productName,
    long quantitySold
) {
}
