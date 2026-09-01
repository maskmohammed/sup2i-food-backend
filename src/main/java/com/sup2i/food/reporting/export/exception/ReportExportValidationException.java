package com.sup2i.food.reporting.export.exception;

public class ReportExportValidationException
    extends RuntimeException {

    public ReportExportValidationException(
        String message
    ) {
        super(message);
    }
}