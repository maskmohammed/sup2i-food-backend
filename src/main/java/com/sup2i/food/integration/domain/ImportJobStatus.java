package com.sup2i.food.integration.domain;

public enum ImportJobStatus {

    PENDING,
    VALIDATING,
    PROCESSING,
    COMPLETED,
    PARTIAL,
    FAILED,
    CANCELLED
}