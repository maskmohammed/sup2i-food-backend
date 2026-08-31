package com.sup2i.food.notification.exception;

public class NotificationConflictException extends RuntimeException {

    public NotificationConflictException(
        String message
    ) {
        super(message);
    }
}