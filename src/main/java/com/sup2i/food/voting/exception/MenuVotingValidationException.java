package com.sup2i.food.voting.exception;

public class MenuVotingValidationException
    extends RuntimeException {

    public MenuVotingValidationException(
        String message
    ) {
        super(message);
    }
}