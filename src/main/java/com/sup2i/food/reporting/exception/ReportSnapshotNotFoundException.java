package com.sup2i.food.reporting.exception;

public class ReportSnapshotNotFoundException
    extends RuntimeException {

    public ReportSnapshotNotFoundException(
        String message
    ) {
        super(message);
    }
}