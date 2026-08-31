package com.sup2i.food.procurement.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderCommand(
    UUID supplierId,
    UUID campusId,
    String reference,
    BigDecimal totalEstimated,
    List<PurchaseOrderItemCommand> items
) {
}