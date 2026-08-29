package com.sup2i.food.scan.exception;

public class ScanException
    extends RuntimeException {

    private final ScanErrorCode errorCode;

    public ScanException(
        ScanErrorCode errorCode,
        String message
    ) {
        super(message);

        this.errorCode =
            errorCode;
    }

    public ScanErrorCode errorCode() {
        return errorCode;
    }
}