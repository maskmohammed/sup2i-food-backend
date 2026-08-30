package com.sup2i.food.inventory.exception;

public class InventoryPublicException
    extends RuntimeException {

    private final InventoryPublicErrorCode
        errorCode;

    public InventoryPublicException(
        InventoryPublicErrorCode errorCode,
        String message
    ) {

        super(message);

        this.errorCode =
            errorCode;
    }

    public InventoryPublicErrorCode
        getErrorCode() {

        return errorCode;
    }
}