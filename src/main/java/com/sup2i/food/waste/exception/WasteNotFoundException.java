package com.sup2i.food.waste.exception;

public class WasteNotFoundException
    extends RuntimeException {

    public WasteNotFoundException(
        String message
    ) {
        super(message);
    }
}