package com.sup2i.food.promotion.exception;

public class CouponIneligibleException
    extends RuntimeException {

    public CouponIneligibleException(
        String message
    ) {
        super(message);
    }
}