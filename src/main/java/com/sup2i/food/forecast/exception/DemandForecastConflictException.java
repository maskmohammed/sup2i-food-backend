package com.sup2i.food.forecast.exception;

public class DemandForecastConflictException
    extends RuntimeException {

    public DemandForecastConflictException(
        String message
    ) {
        super(message);
    }
}