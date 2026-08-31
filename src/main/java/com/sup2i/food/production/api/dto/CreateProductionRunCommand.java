package com.sup2i.food.production.api.dto;

import com.sup2i.food.production.domain.ProductionTargetSource;
import com.sup2i.food.production.domain.ProductionType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateProductionRunCommand(
    UUID campusId,
    UUID serviceLocationId,
    UUID kitchenLocationId,
    UUID canteenMenuId,
    UUID campusEventId,
    LocalDate productionDate,
    ProductionType productionType,
    ProductionTargetSource targetSource,
    String notes,
    List<CreateProductionRunItemCommand> items
) {
}