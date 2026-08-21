package com.sup2i.food.catalog.exception;

public class ProductUnavailableException
    extends RuntimeException {

    public ProductUnavailableException(
        String message
    ) {
        super(message);
    }
}