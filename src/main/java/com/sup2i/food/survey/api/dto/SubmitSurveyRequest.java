package com.sup2i.food.survey.api.dto;

import java.util.Map;

public record SubmitSurveyRequest(
    Map<String, Object> answers
) {
}