package com.sup2i.food.payment.exception;

public class PaymentConflictException
    extends RuntimeException {

    private final PaymentErrorCode errorCode;

    public PaymentConflictException(
        String message
    ) {
        this(
            null,
            message
        );
    }

    public PaymentConflictException(
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