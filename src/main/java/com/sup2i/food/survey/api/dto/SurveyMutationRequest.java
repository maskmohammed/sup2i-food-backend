package com.sup2i.food.survey.api.dto;

import com.sup2i.food.survey.domain.SurveyTarget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record SurveyMutationRequest(
    @NotBlank
    @Size(max = 180)
    String title,

    @Size(max = 4000)
    String description,

    @NotNull
    SurveyTarget target,

    OffsetDateTime startsAt,

    OffsetDateTime endsAt,

    @NotEmpty
    @Valid
    List<SurveyQuestionRequest> questions
) {
}