package com.sup2i.food.waste.exception;

public class WasteValidationException
    extends RuntimeException {

    public WasteValidationException(
        String message
    ) {
        super(message);
    }
}