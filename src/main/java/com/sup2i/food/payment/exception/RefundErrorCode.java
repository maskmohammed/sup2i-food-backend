package com.sup2i.food.payment.exception;

public enum RefundErrorCode {
    RESOURCE_NOT_FOUND,
    VALIDATION_ERROR,
    IDEMPOTENCY_CONFLICT,
    PAYMENT_NOT_REFUNDABLE,
    REFUND_AMOUNT_EXCEEDED
}