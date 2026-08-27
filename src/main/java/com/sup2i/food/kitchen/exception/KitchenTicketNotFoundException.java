package com.sup2i.food.kitchen.exception;

public class KitchenTicketNotFoundException
    extends RuntimeException {

    public KitchenTicketNotFoundException(
        String message
    ) {
        super(message);
    }
}
