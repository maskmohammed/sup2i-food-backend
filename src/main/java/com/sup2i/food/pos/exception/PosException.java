package com.sup2i.food.pos.exception;

public class PosException
    extends RuntimeException {

    private final PosErrorCode errorCode;

    public PosException(
        PosErrorCode errorCode,
        String message
    ) {
        super(message);

        this.errorCode =
            errorCode;
    }

    public PosErrorCode getErrorCode() {
        return errorCode;
    }
}