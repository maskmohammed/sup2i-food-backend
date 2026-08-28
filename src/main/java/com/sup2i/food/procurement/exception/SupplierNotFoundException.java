package com.sup2i.food.procurement.exception;

public class SupplierNotFoundException
    extends RuntimeException {

    public SupplierNotFoundException(
        String message
    ) {
        super(message);
    }
}