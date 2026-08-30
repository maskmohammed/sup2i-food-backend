package com.sup2i.food.promotion.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PromotionResponse(
    UUID id,
    String name,
    String type,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    boolean stackable
) {
}
