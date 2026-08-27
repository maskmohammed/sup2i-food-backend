package com.sup2i.food.payment.exception;

public class PaymentValidationException
    extends RuntimeException {

    public PaymentValidationException(
        String message
    ) {
        super(message);
    }
}
