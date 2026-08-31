package com.sup2i.food.procurement.exception;

public class ProcurementNotFoundException
    extends RuntimeException {

    public ProcurementNotFoundException(
        String message
    ) {
        super(message);
    }
}