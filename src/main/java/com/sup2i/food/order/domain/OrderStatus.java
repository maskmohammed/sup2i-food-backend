package com.sup2i.food.order.domain;

public enum OrderStatus {
    DRAFT,
    CREATED,
    AWAITING_PAYMENT,
    PAID,
    QUEUED,
    PREPARING,
    READY,
    COLLECTED,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    REFUNDED,
    NO_SHOW
}