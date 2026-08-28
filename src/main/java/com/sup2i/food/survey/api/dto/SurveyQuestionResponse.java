package com.sup2i.food.survey.api.dto;

import com.sup2i.food.survey.domain.QuestionType;
import com.sup2i.food.survey.domain.SurveyQuestion;

import java.util.List;
import java.util.UUID;

public record SurveyQuestionResponse(
    UUID id,
    String question,
    QuestionType type,
    List<String> options,
    boolean required
) {

    public static SurveyQuestionResponse from(SurveyQuestion question) {
        return new SurveyQuestionResponse(
            question.getId(),
            question.getQuestion(),
            question.getType(),
            question.optionValues(),
            question.isRequired()
        );
    }
}