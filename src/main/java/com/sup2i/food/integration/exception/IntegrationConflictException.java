package com.sup2i.food.integration.exception;

public class IntegrationConflictException
    extends RuntimeException {

    public IntegrationConflictException(
        String message
    ) {
        super(message);
    }
}