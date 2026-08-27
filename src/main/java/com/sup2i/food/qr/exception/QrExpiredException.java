package com.sup2i.food.qr.exception;

public class QrExpiredException
    extends RuntimeException {

    public QrExpiredException(
        String message
    ) {
        super(message);
    }
}
