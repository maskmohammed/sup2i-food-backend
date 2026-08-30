package com.sup2i.food.slot.exception;

public class TimeSlotException extends RuntimeException {

    private final TimeSlotErrorCode errorCode;

    public TimeSlotException(
        TimeSlotErrorCode errorCode,
        String message
    ) {
        super(message);

        this.errorCode =
            errorCode;
    }

    public TimeSlotErrorCode getErrorCode() {
        return errorCode;
    }
}