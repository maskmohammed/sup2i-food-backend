package com.sup2i.food.review.exception;

public class ReviewValidationException extends RuntimeException {

    public ReviewValidationException(
        String message
    ) {
        super(message);
    }
}