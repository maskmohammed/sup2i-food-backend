package com.sup2i.food.promotion.exception;

public class CouponValidationException
    extends RuntimeException {

    public CouponValidationException(
        String message
    ) {
        super(message);
    }
}