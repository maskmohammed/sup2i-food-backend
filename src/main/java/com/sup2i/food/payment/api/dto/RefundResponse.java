package com.sup2i.food.payment.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundResponse(
    UUID id,
    UUID paymentId,
    BigDecimal amount,
    String status,
    String reason
) {
}