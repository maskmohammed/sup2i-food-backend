package com.sup2i.food.purchase.api.dto;

import com.sup2i.food.purchase.domain.PurchaseOrderReceipt;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderReceiptElement(
    UUID id,
    UUID stockLocationId,
    OffsetDateTime receivedAt,
    UUID receivedBy,
    String notes,
    List<PurchaseOrderReceiptLineElement> lines
) {

    public static PurchaseOrderReceiptElement from(
        PurchaseOrderReceipt receipt
    ) {
        List<PurchaseOrderReceiptLineElement>
            lines =
                receipt
                    .getLines()
                    .stream()
                    .map(
                        PurchaseOrderReceiptLineElement
                            ::from
                    )
                    .toList();

        return new PurchaseOrderReceiptElement(
            receipt.getId(),
            receipt.getStockLocation()
                .getId(),
            receipt.getReceivedAt(),
            receipt.getReceivedBy()
                .getId(),
            receipt.getNotes(),
            lines
        );
    }
}