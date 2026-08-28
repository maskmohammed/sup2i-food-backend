package com.sup2i.food.subscription.exception;

public class SubscriptionNotFoundException
    extends RuntimeException {

    public SubscriptionNotFoundException(
        String message
    ) {
        super(message);
    }
}
