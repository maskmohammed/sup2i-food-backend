package com.sup2i.food.promotion.exception;

public class LoyaltyConflictException
    extends RuntimeException {

    public LoyaltyConflictException(
        String message
    ) {
        super(message);
    }
}