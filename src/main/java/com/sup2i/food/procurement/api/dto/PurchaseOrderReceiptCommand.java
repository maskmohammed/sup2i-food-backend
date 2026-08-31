package com.sup2i.food.procurement.api.dto;

import java.util.List;
import java.util.UUID;

public record PurchaseOrderReceiptCommand(
    UUID receiptId,
    UUID stockLocationId,
    String receiptReference,
    String notes,
    List<PurchaseOrderReceiptLineCommand> lines
) {
}