package com.sup2i.food.forecast.api.dto;

import com.sup2i.food.forecast.domain.ForecastSubjectType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateDemandForecastCommand(
    UUID locationId,
    ForecastSubjectType subjectType,
    UUID subjectId,
    LocalDate forecastDate,
    UUID timeSlotId,
    BigDecimal predictedQuantity,
    BigDecimal confidenceScore,
    String modelName,
    String modelVersion,
    String featuresSnapshotJson
) {
}