package com.sup2i.food.payment.exception;

public class RefundException
    extends RuntimeException {

    private final RefundErrorCode errorCode;

    public RefundException(
        RefundErrorCode errorCode,
        String message
    ) {
        super(
            message
        );

        this.errorCode =
            errorCode;
    }

    public RefundErrorCode getErrorCode() {
        return errorCode;
    }
}