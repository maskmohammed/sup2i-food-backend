package com.sup2i.food.integration.exception;

public class IntegrationNotFoundException
    extends RuntimeException {

    public IntegrationNotFoundException(
        String message
    ) {
        super(message);
    }
}