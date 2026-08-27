package com.sup2i.food.payment.exception;

public class PaymentConflictException
    extends RuntimeException {

    public PaymentConflictException(
        String message
    ) {
        super(message);
    }
}
