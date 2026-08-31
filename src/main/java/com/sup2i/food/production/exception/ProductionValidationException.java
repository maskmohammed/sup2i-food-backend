package com.sup2i.food.production.exception;

public class ProductionValidationException
    extends RuntimeException {

    public ProductionValidationException(
        String message
    ) {
        super(message);
    }
}