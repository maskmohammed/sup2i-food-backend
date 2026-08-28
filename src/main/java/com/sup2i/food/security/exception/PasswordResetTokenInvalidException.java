package com.sup2i.food.security.exception;

public class PasswordResetTokenInvalidException
    extends RuntimeException {

    public PasswordResetTokenInvalidException(
        String message
    ) {
        super(message);
    }
}
