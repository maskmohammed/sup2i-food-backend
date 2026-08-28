package com.sup2i.food.review.exception;

public class ReviewNotFoundException
    extends RuntimeException {

    public ReviewNotFoundException(
        String message
    ) {
        super(message);
    }
}