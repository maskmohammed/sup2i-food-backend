package com.sup2i.food.promotion.exception;

public class LoyaltyValidationException
    extends RuntimeException {

    public LoyaltyValidationException(
        String message
    ) {
        super(message);
    }
}