package com.sup2i.food.reporting.export.exception;

public class ReportExportNotFoundException
    extends RuntimeException {

    public ReportExportNotFoundException(
        String message
    ) {
        super(message);
    }
}