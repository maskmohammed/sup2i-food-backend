package com.sup2i.food.purchase.exception;

public class PurchaseConflictException
    extends RuntimeException {

    public PurchaseConflictException(
        String message
    ) {
        super(message);
    }
}