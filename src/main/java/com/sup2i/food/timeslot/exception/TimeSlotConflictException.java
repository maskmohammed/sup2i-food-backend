package com.sup2i.food.timeslot.exception;

public class TimeSlotConflictException
    extends RuntimeException {

    public TimeSlotConflictException(
        String message
    ) {
        super(message);
    }
}
