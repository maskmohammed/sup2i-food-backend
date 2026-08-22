package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.inventory.domain.StockReceiptStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StockReceiptResponse(
    UUID id,
    UUID stockLocationId,
    UUID supplierId,
    String supplierName,
    String receiptReference,
    OffsetDateTime receivedAt,
    UUID receivedBy,
    StockReceiptStatus status,
    String notes,
    List<StockReceiptLineResponse> lines
) {
}