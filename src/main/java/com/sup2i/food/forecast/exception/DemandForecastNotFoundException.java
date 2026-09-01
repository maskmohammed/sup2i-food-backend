package com.sup2i.food.forecast.exception;

public class DemandForecastNotFoundException
    extends RuntimeException {

    public DemandForecastNotFoundException(
        String message
    ) {
        super(message);
    }
}