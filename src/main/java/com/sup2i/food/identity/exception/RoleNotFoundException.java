package com.sup2i.food.identity.exception;

public class RoleNotFoundException
    extends RuntimeException {

    public RoleNotFoundException(
        String message
    ) {
        super(message);
    }
}
