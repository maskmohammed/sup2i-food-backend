package com.sup2i.food.review.exception;

public class ReviewConflictException extends RuntimeException {

    public ReviewConflictException(
        String message
    ) {
        super(message);
    }
}