package com.sup2i.food.survey.exception;

public class SurveyValidationException
    extends RuntimeException {

    public SurveyValidationException(
        String message
    ) {
        super(message);
    }
}