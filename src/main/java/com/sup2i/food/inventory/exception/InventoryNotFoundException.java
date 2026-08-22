package com.sup2i.food.inventory.exception;

public class InventoryNotFoundException
    extends RuntimeException {

    public InventoryNotFoundException(
        String message
    ) {
        super(message);
    }
}