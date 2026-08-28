package com.sup2i.food.survey.exception;

public class SurveyNotFoundException
    extends RuntimeException {

    public SurveyNotFoundException(
        String message
    ) {
        super(message);
    }
}