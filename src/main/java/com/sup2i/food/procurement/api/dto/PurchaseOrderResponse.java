package com.sup2i.food.procurement.api.dto;

import com.sup2i.food.procurement.domain.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
    UUID id,
    UUID supplierId,
    UUID campusId,
    String reference,
    PurchaseOrderStatus status,
    BigDecimal totalEstimated,
    UUID createdBy,
    UUID approvedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<PurchaseOrderItemResponse> items
) {
}