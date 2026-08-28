package com.sup2i.food.procurement.exception;

public class SupplierValidationException
    extends RuntimeException {

    public SupplierValidationException(
        String message
    ) {
        super(message);
    }
}