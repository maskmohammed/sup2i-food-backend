package com.sup2i.food.reporting.exception;

public class ReportSnapshotConflictException
    extends RuntimeException {

    public ReportSnapshotConflictException(
        String message
    ) {
        super(message);
    }
}