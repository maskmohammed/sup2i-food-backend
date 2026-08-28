package com.sup2i.food.notification.exception;

public class NotificationNotFoundException
    extends RuntimeException {

    public NotificationNotFoundException(
        String message
    ) {
        super(message);
    }
}
