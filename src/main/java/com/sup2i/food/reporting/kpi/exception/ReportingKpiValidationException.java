package com.sup2i.food.reporting.kpi.exception;

public class ReportingKpiValidationException
    extends RuntimeException {

    public ReportingKpiValidationException(
        String message
    ) {
        super(message);
    }
}