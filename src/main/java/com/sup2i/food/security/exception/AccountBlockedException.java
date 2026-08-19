package com.sup2i.food.security.exception;

import org.springframework.security.core.AuthenticationException;

public class AccountBlockedException
    extends AuthenticationException {

    public AccountBlockedException(String message) {
        super(message);
    }
}