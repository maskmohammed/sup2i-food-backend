package com.sup2i.food.security.exception;

import org.springframework.security.core.AuthenticationException;

public class LoginRateLimitedException
    extends AuthenticationException {

    public LoginRateLimitedException(String message) {
        super(message);
    }
}