package com.sup2i.food.payment.service;

import com.sup2i.food.payment.domain.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCaptureCommand(
    PaymentMethod method,
    String idempotencyKey,
    BigDecimal amount,
    String externalReference,
    UUID posSessionId
) {
}