package com.sup2i.food.interaction.exception;

public class ProductInteractionConflictException
    extends RuntimeException {

    public ProductInteractionConflictException(
        String message
    ) {
        super(message);
    }
}