package com.sup2i.food.eventing.exception;

public class GroupEventConflictException extends RuntimeException {

    public GroupEventConflictException(String message) {
        super(message);
    }
}