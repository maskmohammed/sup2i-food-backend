package com.sup2i.food.purchase.domain;

public enum PurchaseOrderHistoryEvent {
    CREATED,
    UPDATED,
    SENT,
    CONFIRMED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED
}