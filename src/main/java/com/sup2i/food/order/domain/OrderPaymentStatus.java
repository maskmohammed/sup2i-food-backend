package com.sup2i.food.order.domain;

public enum OrderPaymentStatus {
    NOT_REQUIRED,
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED
}