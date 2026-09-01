package com.sup2i.food.survey.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SurveySubmissionResponse(
    UUID id,
    UUID surveyId,
    UUID studentId,
    String answersJson,
    OffsetDateTime submittedAt,
    boolean replayed
) {
}