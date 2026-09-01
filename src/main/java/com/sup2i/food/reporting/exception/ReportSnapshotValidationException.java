package com.sup2i.food.reporting.exception;

public class ReportSnapshotValidationException
    extends RuntimeException {

    public ReportSnapshotValidationException(
        String message
    ) {
        super(message);
    }
}