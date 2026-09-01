package com.sup2i.food.forecast.exception;

public class DemandForecastValidationException
    extends RuntimeException {

    public DemandForecastValidationException(
        String message
    ) {
        super(message);
    }
}