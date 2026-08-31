package com.sup2i.food.production.exception;

public class ProductionNotFoundException
    extends RuntimeException {

    public ProductionNotFoundException(
        String message
    ) {
        super(message);
    }
}