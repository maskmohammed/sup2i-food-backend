package com.sup2i.food.kitchen.exception;

public class KitchenTicketConflictException
    extends RuntimeException {

    public KitchenTicketConflictException(
        String message
    ) {
        super(message);
    }
}
