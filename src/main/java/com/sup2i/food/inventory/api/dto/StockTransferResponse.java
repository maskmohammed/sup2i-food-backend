package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.inventory.domain.StockTransferStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StockTransferResponse(
    UUID id,
    UUID sourceStockLocationId,
    UUID destinationStockLocationId,
    StockTransferStatus status,
    UUID requestedBy,
    UUID approvedBy,
    UUID dispatchedBy,
    UUID receivedBy,
    OffsetDateTime requestedAt,
    OffsetDateTime dispatchedAt,
    OffsetDateTime receivedAt,
    OffsetDateTime cancelledAt,
    String reason,
    List<StockTransferLineResponse> lines
) {
}