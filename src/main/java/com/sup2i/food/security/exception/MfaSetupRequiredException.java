package com.sup2i.food.security.exception;

import org.springframework.security.core.AuthenticationException;

public class MfaSetupRequiredException
    extends AuthenticationException {

    public MfaSetupRequiredException(
        String message
    ) {
        super(message);
    }
}