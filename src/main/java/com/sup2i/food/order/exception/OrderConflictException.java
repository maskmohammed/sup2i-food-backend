package com.sup2i.food.order.exception;

public class OrderConflictException
    extends RuntimeException {

    public OrderConflictException(
        String message
    ) {
        super(message);
    }
}