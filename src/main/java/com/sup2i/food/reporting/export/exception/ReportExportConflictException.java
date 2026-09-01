package com.sup2i.food.reporting.export.exception;

public class ReportExportConflictException
    extends RuntimeException {

    public ReportExportConflictException(
        String message
    ) {
        super(message);
    }
}