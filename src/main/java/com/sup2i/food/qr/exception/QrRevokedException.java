package com.sup2i.food.qr.exception;

public class QrRevokedException
    extends RuntimeException {

    public QrRevokedException(
        String message
    ) {
        super(message);
    }
}
