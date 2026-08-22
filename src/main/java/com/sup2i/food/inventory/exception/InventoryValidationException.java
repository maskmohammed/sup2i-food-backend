package com.sup2i.food.inventory.exception;

public class InventoryValidationException
    extends RuntimeException {

    public InventoryValidationException(
        String message
    ) {
        super(message);
    }
}