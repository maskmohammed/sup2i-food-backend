package com.sup2i.food.kitchen.exception;

public class KitchenNotFoundException
    extends RuntimeException {

    private final KitchenErrorCode errorCode;

    public KitchenNotFoundException(
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