package com.sup2i.food.payment.exception;

public class PaymentNotFoundException
    extends RuntimeException {

    public PaymentNotFoundException(
        String message
    ) {
        super(message);
    }
}
