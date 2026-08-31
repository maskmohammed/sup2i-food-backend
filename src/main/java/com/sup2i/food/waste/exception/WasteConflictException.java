package com.sup2i.food.waste.exception;

public class WasteConflictException
    extends RuntimeException {

    public WasteConflictException(
        String message
    ) {
        super(message);
    }
}