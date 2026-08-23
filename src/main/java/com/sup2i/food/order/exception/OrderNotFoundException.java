package com.sup2i.food.order.exception;

public class OrderNotFoundException
    extends RuntimeException {

    public OrderNotFoundException(
        String message
    ) {
        super(message);
    }
}