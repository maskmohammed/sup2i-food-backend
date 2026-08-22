package com.sup2i.food.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record CreateStockTransferRequest(

    @NotNull
    UUID sourceStockLocationId,

    @NotNull
    UUID destinationStockLocationId,

    @Size(max = 5000)
    String reason,

    @NotEmpty
    List<
        @NotNull
        @Valid
        CreateStockTransferLineRequest
    > lines
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
            CreateStockTransferLineRequest line
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

    @AssertTrue(
        message =
            "stockItemId values must be unique"
    )
    public boolean isStockItemIdsUnique() {

        if (lines == null) {
            return true;
        }

        HashSet<UUID> ids =
            new HashSet<>();

        for (
            CreateStockTransferLineRequest line
            : lines
        ) {

            if (
                line != null
                && line.stockItemId() != null
                && !ids.add(
                    line.stockItemId()
                )
            ) {
                return false;
            }
        }

        return true;
    }

    @AssertTrue(
        message =
            "source and destination stock locations must differ"
    )
    public boolean isLocationPairValid() {

        if (
            sourceStockLocationId == null
            || destinationStockLocationId
                == null
        ) {
            return true;
        }

        return !sourceStockLocationId
            .equals(
                destinationStockLocationId
            );
    }
}