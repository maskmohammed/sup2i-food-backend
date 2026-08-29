package com.sup2i.food.pos.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PosCheckoutResponse(
    UUID paymentId,
    UUID orderId,
    UUID posSessionId,
    String method,
    String status,
    BigDecimal amount,
    String currency,
    BigDecimal tenderedAmount,
    BigDecimal changeAmount,
    OffsetDateTime paidAt,
    boolean replayed,
    PosReceiptResponse receipt
) {
}