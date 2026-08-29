package com.sup2i.food.pos.exception;

public class PosValidationException
    extends RuntimeException {

    public PosValidationException(
        String message
    ) {
        super(message);
    }
}