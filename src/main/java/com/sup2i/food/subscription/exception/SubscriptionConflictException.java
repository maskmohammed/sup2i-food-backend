package com.sup2i.food.subscription.exception;

public class SubscriptionConflictException
    extends RuntimeException {

    public SubscriptionConflictException(
        String message
    ) {
        super(message);
    }
}
