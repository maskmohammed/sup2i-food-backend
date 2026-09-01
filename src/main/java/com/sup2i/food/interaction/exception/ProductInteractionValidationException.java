package com.sup2i.food.interaction.exception;

public class ProductInteractionValidationException
    extends RuntimeException {

    public ProductInteractionValidationException(
        String message
    ) {
        super(message);
    }
}