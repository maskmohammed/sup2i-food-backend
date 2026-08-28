package com.sup2i.food.purchase.exception;

public class PurchaseValidationException
    extends RuntimeException {

    public PurchaseValidationException(
        String message
    ) {
        super(message);
    }
}