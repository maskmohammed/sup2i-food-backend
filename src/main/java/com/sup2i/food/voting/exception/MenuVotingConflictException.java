package com.sup2i.food.voting.exception;

public class MenuVotingConflictException
    extends RuntimeException {

    public MenuVotingConflictException(
        String message
    ) {
        super(message);
    }
}