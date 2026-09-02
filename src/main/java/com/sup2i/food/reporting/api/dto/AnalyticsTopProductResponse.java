package com.sup2i.food.reporting.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AnalyticsTopProductResponse(
    UUID productId,
    String name,
    long quantity,
    BigDecimal revenue
) {
}