package com.sup2i.food.order.exception;

public class OrderValidationException
    extends RuntimeException {

    public OrderValidationException(
        String message
    ) {
        super(message);
    }
}