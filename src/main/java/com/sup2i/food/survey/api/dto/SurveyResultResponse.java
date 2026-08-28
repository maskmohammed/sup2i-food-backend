package com.sup2i.food.survey.api.dto;

import com.sup2i.food.survey.domain.QuestionType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SurveyResultResponse(
    UUID surveyId,
    String title,
    List<QuestionResult> questions
) {

    public record QuestionResult(
        UUID questionId,
        String question,
        QuestionType type,
        int totalResponses,
        Map<String, Integer> counts,
        List<String> values
    ) {
    }
}