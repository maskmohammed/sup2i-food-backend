package com.sup2i.food.subscription.exception;

public class SubscriptionValidationException
    extends RuntimeException {

    public SubscriptionValidationException(
        String message
    ) {
        super(message);
    }
}
