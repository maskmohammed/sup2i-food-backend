package com.sup2i.food.promotion.exception;

public class CouponNotFoundException
    extends RuntimeException {

    public CouponNotFoundException(
        String message
    ) {
        super(message);
    }
}