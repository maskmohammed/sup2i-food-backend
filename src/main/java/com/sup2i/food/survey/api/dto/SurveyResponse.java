package com.sup2i.food.survey.api.dto;

import com.sup2i.food.survey.domain.SurveyStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SurveyResponse(
    UUID id,
    UUID organizationId,
    String title,
    String description,
    SurveyStatus status,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    boolean replayed
) {
}