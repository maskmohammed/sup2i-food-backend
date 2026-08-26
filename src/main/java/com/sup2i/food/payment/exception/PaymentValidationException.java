package com.sup2i.food.payment.exception;

public class PaymentValidationException
    extends RuntimeException {

    private final PaymentErrorCode errorCode;

    public PaymentValidationException(
        String message
    ) {
        this(
            null,
            message
        );
    }

    public PaymentValidationException(
        PaymentErrorCode errorCode,
        String message
    ) {
        super(message);

        this.errorCode =
            errorCode;
    }

    public PaymentErrorCode getErrorCode() {
        return errorCode;
    }
}