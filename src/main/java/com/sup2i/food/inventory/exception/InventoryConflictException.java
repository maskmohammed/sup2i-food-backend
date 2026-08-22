package com.sup2i.food.inventory.exception;

public class InventoryConflictException
    extends RuntimeException {

    public InventoryConflictException(
        String message
    ) {
        super(message);
    }
}