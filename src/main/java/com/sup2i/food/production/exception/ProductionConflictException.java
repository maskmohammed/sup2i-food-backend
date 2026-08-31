package com.sup2i.food.production.exception;

public class ProductionConflictException
    extends RuntimeException {

    public ProductionConflictException(
        String message
    ) {
        super(message);
    }
}