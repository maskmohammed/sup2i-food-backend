package com.sup2i.food.survey.api.dto;

import com.sup2i.food.survey.domain.SurveyQuestionType;

import java.util.UUID;

public record SurveyQuestionResponse(
    UUID id,
    UUID surveyId,
    String question,
    SurveyQuestionType type,
    String optionsJson,
    int displayOrder,
    boolean required,
    boolean replayed
) {
}