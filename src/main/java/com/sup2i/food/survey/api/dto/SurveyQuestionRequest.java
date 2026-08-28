package com.sup2i.food.survey.api.dto;

import com.sup2i.food.survey.domain.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SurveyQuestionRequest(
    @NotBlank
    @Size(max = 500)
    String question,

    @NotNull
    QuestionType type,

    List<@Size(max = 200) String> options,

    boolean required
) {
}