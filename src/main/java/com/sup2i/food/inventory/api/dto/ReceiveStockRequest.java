package com.sup2i.food.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record ReceiveStockRequest(

    @NotNull
    UUID stockLocationId,

    UUID supplierId,

    @Size(max = 100)
    String receiptReference,

    String notes,

    @NotEmpty
    List<@NotNull @Valid ReceiveStockLineRequest> lines
) {

    @AssertTrue(
        message =
            "lineId values must be unique"
    )
    public boolean isLineIdsUnique() {

        if (lines == null) {
            return true;
        }

        HashSet<UUID> ids =
            new HashSet<>();

        for (
            ReceiveStockLineRequest line
            : lines
        ) {

            if (
                line != null
                && line.lineId() != null
                && !ids.add(
                    line.lineId()
                )
            ) {
                return false;
            }
        }

        return true;
    }
}