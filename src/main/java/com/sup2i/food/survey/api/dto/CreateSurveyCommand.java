package com.sup2i.food.survey.api.dto;

import java.time.OffsetDateTime;

public record CreateSurveyCommand(
    String title,
    String description,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt
) {
}