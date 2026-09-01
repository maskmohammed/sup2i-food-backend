package com.sup2i.food.survey.api.dto;

import com.sup2i.food.survey.domain.SurveyQuestionType;

public record AddSurveyQuestionCommand(
    String question,
    SurveyQuestionType type,
    String optionsJson,
    int displayOrder,
    boolean required
) {
}