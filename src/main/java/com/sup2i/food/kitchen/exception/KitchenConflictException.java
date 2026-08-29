package com.sup2i.food.kitchen.exception;

public class KitchenConflictException
    extends RuntimeException {

    private final KitchenErrorCode errorCode;

    public KitchenConflictException(
        KitchenErrorCode errorCode,
        String message
    ) {
        super(message);

        this.errorCode =
            errorCode;
    }

    public KitchenErrorCode getErrorCode() {
        return errorCode;
    }
}