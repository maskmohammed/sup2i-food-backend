package com.sup2i.food.payment.service;

import com.sup2i.food.payment.domain.PaymentMethod;
import com.sup2i.food.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentCaptureResult(
    UUID paymentId,
    UUID orderId,
    UUID posSessionId,
    PaymentMethod method,
    PaymentStatus status,
    BigDecimal amount,
    String currency,
    String externalReference,
    OffsetDateTime paidAt,
    boolean replayed
) {
}