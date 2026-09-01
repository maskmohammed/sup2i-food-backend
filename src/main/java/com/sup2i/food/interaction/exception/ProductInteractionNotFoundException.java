package com.sup2i.food.interaction.exception;

public class ProductInteractionNotFoundException
    extends RuntimeException {

    public ProductInteractionNotFoundException(
        String message
    ) {
        super(message);
    }
}