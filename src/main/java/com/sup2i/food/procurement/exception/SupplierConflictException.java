package com.sup2i.food.procurement.exception;

public class SupplierConflictException
    extends RuntimeException {

    public SupplierConflictException(
        String message
    ) {
        super(message);
    }
}