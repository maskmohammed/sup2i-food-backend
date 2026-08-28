package com.sup2i.food.menuvote.exception;

public class MenuVoteValidationException
    extends RuntimeException {

    public MenuVoteValidationException(
        String message
    ) {
        super(message);
    }
}