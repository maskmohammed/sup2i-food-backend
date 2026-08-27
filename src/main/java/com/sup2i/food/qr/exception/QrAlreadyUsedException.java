package com.sup2i.food.qr.exception;

public class QrAlreadyUsedException
    extends RuntimeException {

    public QrAlreadyUsedException(
        String message
    ) {
        super(message);
    }
}
