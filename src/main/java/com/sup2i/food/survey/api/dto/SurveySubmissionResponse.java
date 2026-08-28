package com.sup2i.food.survey.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SurveySubmissionResponse(
    UUID surveyId,
    OffsetDateTime submittedAt
) {
}