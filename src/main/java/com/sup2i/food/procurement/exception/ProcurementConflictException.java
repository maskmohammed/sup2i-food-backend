package com.sup2i.food.procurement.exception;

public class ProcurementConflictException
    extends RuntimeException {

    public ProcurementConflictException(
        String message
    ) {
        super(message);
    }
}