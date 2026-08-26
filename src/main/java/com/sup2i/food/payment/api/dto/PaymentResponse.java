package com.sup2i.food.payment.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID orderId,
    String method,
    String status,
    BigDecimal amount,
    String currency,
    OffsetDateTime paidAt
) {
}