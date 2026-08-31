package com.sup2i.food.procurement.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierResponse(
    UUID id,
    UUID organizationId,
    String name,
    String phone,
    String email,
    String address,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}