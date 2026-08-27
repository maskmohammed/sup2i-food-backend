package com.sup2i.food.qr.exception;

public class QrConflictException
    extends RuntimeException {

    public QrConflictException(
        String message
    ) {
        super(message);
    }
}
