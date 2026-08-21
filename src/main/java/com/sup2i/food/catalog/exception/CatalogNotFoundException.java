package com.sup2i.food.catalog.exception;

public class CatalogNotFoundException
    extends RuntimeException {

    public CatalogNotFoundException(
        String message
    ) {
        super(message);
    }
}