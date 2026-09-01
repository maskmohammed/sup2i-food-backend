package com.sup2i.food.forecast.api.dto;

import com.sup2i.food.forecast.domain.ForecastSubjectType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DemandForecastResponse(
    UUID id,
    UUID locationId,
    ForecastSubjectType subjectType,
    UUID subjectId,
    LocalDate forecastDate,
    UUID timeSlotId,
    BigDecimal predictedQuantity,
    BigDecimal confidenceScore,
    String modelName,
    String modelVersion,
    String featuresSnapshotJson,
    OffsetDateTime generatedAt,
    boolean replayed
) {
}