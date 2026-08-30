package com.sup2i.food.canteen.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CanteenReservationResponse(
    UUID id,
    UUID menuId,
    UUID studentId,
    String status,
    OffsetDateTime reservedAt
) {
}
