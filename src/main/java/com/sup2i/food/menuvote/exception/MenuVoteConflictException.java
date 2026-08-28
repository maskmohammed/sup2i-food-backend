package com.sup2i.food.menuvote.exception;

public class MenuVoteConflictException
    extends RuntimeException {

    public MenuVoteConflictException(
        String message
    ) {
        super(message);
    }
}