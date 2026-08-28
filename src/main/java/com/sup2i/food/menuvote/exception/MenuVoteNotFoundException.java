package com.sup2i.food.menuvote.exception;

public class MenuVoteNotFoundException
    extends RuntimeException {

    public MenuVoteNotFoundException(
        String message
    ) {
        super(message);
    }
}