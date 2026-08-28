package com.sup2i.food.waste.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure server-side cost estimator for waste records.
 * The client never submits a cost; the service resolves the
 * latest known unit cost and delegates the arithmetic here.
 */
public final class WasteCostCalculator {

    private WasteCostCalculator() {
    }

    public static BigDecimal estimate(
        BigDecimal quantity,
        BigDecimal unitCost
    ) {
        BigDecimal safeQuantity =
            quantity == null
                ? BigDecimal.ZERO
                : quantity;

        BigDecimal safeUnitCost =
            unitCost == null
                ? BigDecimal.ZERO
                : unitCost;

        return safeQuantity
            .multiply(safeUnitCost)
            .setScale(
                2,
                RoundingMode.HALF_UP
            );
    }
}