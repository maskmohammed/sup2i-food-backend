package com.sup2i.food.security.exception;

import org.springframework.security.core.AuthenticationException;

public class MfaRequiredException
    extends AuthenticationException {

    public MfaRequiredException(
        String message
    ) {
        super(message);
    }
}