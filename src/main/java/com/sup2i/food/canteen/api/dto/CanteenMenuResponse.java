package com.sup2i.food.canteen.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CanteenMenuResponse(
    UUID id,
    LocalDate date,
    String mealType,
    String title,
    String description,
    String status,
    List<CanteenProductSummaryResponse> products
) {
}
