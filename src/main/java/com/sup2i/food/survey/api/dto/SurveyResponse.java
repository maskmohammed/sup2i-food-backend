package com.sup2i.food.survey.api.dto;

import com.sup2i.food.survey.domain.Survey;
import com.sup2i.food.survey.domain.SurveyStatus;
import com.sup2i.food.survey.domain.SurveyTarget;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SurveyResponse(
    UUID id,
    String title,
    String description,
    SurveyStatus status,
    SurveyTarget target,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    OffsetDateTime createdAt,
    List<SurveyQuestionResponse> questions
) {

    public static SurveyResponse from(Survey survey) {
        return new SurveyResponse(
            survey.getId(),
            survey.getTitle(),
            survey.getDescription(),
            survey.getStatus(),
            survey.getTarget(),
            survey.getStartsAt(),
            survey.getEndsAt(),
            survey.getCreatedAt(),
            survey.getQuestions()
                .stream()
                .map(SurveyQuestionResponse::from)
                .toList()
        );
    }
}