package com.sup2i.food.promotion.exception;

public class CouponUsageLimitException
    extends RuntimeException {

    public CouponUsageLimitException(
        String message
    ) {
        super(message);
    }
}