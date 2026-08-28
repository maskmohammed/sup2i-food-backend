package com.sup2i.food.identity.exception;

public class UserNotFoundException
    extends RuntimeException {

    public UserNotFoundException(
        String message
    ) {
        super(message);
    }
}
