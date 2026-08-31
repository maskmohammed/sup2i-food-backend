package com.sup2i.food.voting.exception;

public class MenuVotingNotFoundException
    extends RuntimeException {

    public MenuVotingNotFoundException(
        String message
    ) {
        super(message);
    }
}