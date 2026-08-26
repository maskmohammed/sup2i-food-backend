package com.sup2i.food.payment.exception;

import java.util.Objects;

public class PaymentNotFoundException
    extends RuntimeException {

    private final PaymentErrorCode errorCode;

    public PaymentNotFoundException(
        PaymentErrorCode errorCode,
        String message
    ) {
        super(message);

        this.errorCode =
            Objects.requireNonNull(
                errorCode,
                "errorCode"
            );
    }

    public PaymentErrorCode getErrorCode() {
        return errorCode;
    }
}