package com.sup2i.food.notification.domain;

/**
 * Functional notification event codes defined by the SUP2I FOOD
 * product specification.
 *
 * These values are persisted through notifications.type.
 */
public enum NotificationEventType {

    ORDER_CREATED,
    PAYMENT_CONFIRMED,
    PREPARATION_STARTED,
    ORDER_READY,
    SLOT_SOON,
    ORDER_EXPIRED,
    ORDER_CANCELLED,
    CANTEEN_MENU_PUBLISHED,
    CANTEEN_RESERVATION_REMINDER,
    CANTEEN_REDEMPTION,
    SUBSCRIPTION_EXPIRING,
    QUOTA_LOW,
    FOOD_PASS_BLOCKED,
    LOYALTY_REWARD_AVAILABLE,
    PROMOTION
}