package com.sup2i.food.kitchen.exception;

public enum KitchenErrorCode {

    KITCHEN_TICKET_NOT_FOUND,
    INVALID_KITCHEN_STATUS,
    INVALID_ORDER_STATUS,
    OUT_OF_STOCK,
    IDEMPOTENCY_CONFLICT,
    CONCURRENT_MODIFICATION
}