package com.sup2i.food.purchase.exception;

public class PurchaseNotFoundException
    extends RuntimeException {

    public PurchaseNotFoundException(
        String message
    ) {
        super(message);
    }
}