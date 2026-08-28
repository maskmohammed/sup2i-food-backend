package com.sup2i.food.timeslot.exception;

public class TimeSlotNotFoundException
    extends RuntimeException {

    public TimeSlotNotFoundException(
        String message
    ) {
        super(message);
    }
}
