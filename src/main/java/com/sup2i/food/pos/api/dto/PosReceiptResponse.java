package com.sup2i.food.pos.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PosReceiptResponse(
    UUID id,
    String receiptNumber,
    UUID orderId,
    UUID paymentId,
    UUID posSessionId,
    LocalDate businessDate,
    OffsetDateTime issuedAt,
    String status
) {
}