package com.sup2i.food.integration.exception;

public class IntegrationValidationException
    extends RuntimeException {

    public IntegrationValidationException(
        String message
    ) {
        super(message);
    }
}