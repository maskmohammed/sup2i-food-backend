package com.sup2i.food.purchase.api.dto;

import com.sup2i.food.purchase.domain.PurchaseOrderHistory;
import com.sup2i.food.purchase.domain.PurchaseOrderHistoryEvent;
import com.sup2i.food.purchase.domain.PurchaseOrderStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseOrderHistoryElement(
    UUID id,
    PurchaseOrderHistoryEvent eventType,
    PurchaseOrderStatus statusBefore,
    PurchaseOrderStatus statusAfter,
    UUID performedBy,
    OffsetDateTime occurredAt,
    String notes
) {

    public static PurchaseOrderHistoryElement from(
        PurchaseOrderHistory history
    ) {
        return new PurchaseOrderHistoryElement(
            history.getId(),
            history.getEventType(),
            history.getStatusBefore(),
            history.getStatusAfter(),
            history.getPerformedBy()
                .getId(),
            history.getOccurredAt(),
            history.getNotes()
        );
    }
}