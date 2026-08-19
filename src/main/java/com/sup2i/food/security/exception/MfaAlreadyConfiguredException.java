package com.sup2i.food.security.exception;

public class MfaAlreadyConfiguredException
    extends RuntimeException {

    public MfaAlreadyConfiguredException(
        String message
    ) {
        super(message);
    }
}