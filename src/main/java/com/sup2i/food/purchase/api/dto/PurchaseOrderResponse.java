package com.sup2i.food.purchase.api.dto;

import com.sup2i.food.purchase.domain.PurchaseOrder;
import com.sup2i.food.purchase.domain.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
    UUID id,
    UUID supplierId,
    String supplierName,
    UUID campusId,
    String reference,
    PurchaseOrderStatus status,
    BigDecimal totalEstimated,
    String notes,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<PurchaseOrderLineResponse> lines,
    List<PurchaseOrderHistoryElement> history,
    List<PurchaseOrderReceiptElement> receipts
) {

    public static PurchaseOrderResponse from(
        PurchaseOrder purchaseOrder,
        List<PurchaseOrderHistoryElement> history,
        List<PurchaseOrderReceiptElement> receipts
    ) {
        List<PurchaseOrderLineResponse>
            lines =
                purchaseOrder
                    .getLines()
                    .stream()
                    .map(
                        PurchaseOrderLineResponse
                            ::from
                    )
                    .toList();

        return new PurchaseOrderResponse(
            purchaseOrder.getId(),
            purchaseOrder.getSupplier()
                .getId(),
            purchaseOrder.getSupplier()
                .getName(),
            purchaseOrder.getCampus()
                .getId(),
            purchaseOrder.getReference(),
            purchaseOrder.getStatus(),
            purchaseOrder.getTotalEstimated(),
            purchaseOrder.getNotes(),
            purchaseOrder.getCreatedBy()
                .getId(),
            purchaseOrder.getCreatedAt(),
            purchaseOrder.getUpdatedAt(),
            lines,
            history,
            receipts
        );
    }
}