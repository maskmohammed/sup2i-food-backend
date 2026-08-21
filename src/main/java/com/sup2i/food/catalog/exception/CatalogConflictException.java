package com.sup2i.food.catalog.exception;

public class CatalogConflictException
    extends RuntimeException {

    public CatalogConflictException(
        String message
    ) {
        super(message);
    }
}