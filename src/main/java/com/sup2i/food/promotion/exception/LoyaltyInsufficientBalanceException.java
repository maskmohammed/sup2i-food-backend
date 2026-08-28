package com.sup2i.food.promotion.exception;

public class LoyaltyInsufficientBalanceException
    extends RuntimeException {

    public LoyaltyInsufficientBalanceException(
        String message
    ) {
        super(message);
    }
}