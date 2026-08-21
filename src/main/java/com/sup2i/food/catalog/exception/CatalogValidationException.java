package com.sup2i.food.catalog.exception;

public class CatalogValidationException
    extends RuntimeException {

    public CatalogValidationException(
        String message
    ) {
        super(message);
    }
}