package com.sup2i.food.qr.exception;

public class QrNotFoundException
    extends RuntimeException {

    public QrNotFoundException(
        String message
    ) {
        super(message);
    }
}
