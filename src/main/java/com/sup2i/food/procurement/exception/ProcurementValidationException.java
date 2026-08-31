package com.sup2i.food.procurement.exception;

public class ProcurementValidationException
    extends RuntimeException {

    public ProcurementValidationException(
        String message
    ) {
        super(message);
    }
}