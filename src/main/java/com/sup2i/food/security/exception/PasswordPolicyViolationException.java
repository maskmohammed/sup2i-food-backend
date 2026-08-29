package com.sup2i.food.security.exception;

public class PasswordPolicyViolationException
    extends RuntimeException {

    public PasswordPolicyViolationException(
        String message
    ) {
        super(message);
    }
}