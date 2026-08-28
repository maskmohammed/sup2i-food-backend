package com.sup2i.food.purchase.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure arithmetic for purchase orders (validated through unit tests).
 * All money figures are server-computed and rounded HALF_UP to 2 decimals.
 */
public final class PurchaseOrderCalculator {

    private static final int MONEY_SCALE = 2;

    private PurchaseOrderCalculator() {
    }

    public static BigDecimal lineTotal(
        BigDecimal quantity,
        BigDecimal unitPrice
    ) {
        if (unitPrice == null) {
            return null;
        }

        BigDecimal safeQuantity =
            quantity == null
                ? BigDecimal.ZERO
                : quantity;

        return safeQuantity
            .multiply(unitPrice)
            .setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
            );
    }

    public static BigDecimal orderTotal(
        List<BigDecimal> lineTotals
    ) {
        if (
            lineTotals == null
            || lineTotals.isEmpty()
        ) {
            return BigDecimal.ZERO
                .setScale(
                    MONEY_SCALE
                );
        }

        BigDecimal total =
            BigDecimal.ZERO;

        for (
            BigDecimal lineTotal
            : lineTotals
        ) {
            if (lineTotal != null) {
                total =
                    total.add(lineTotal);
            }
        }

        return total.setScale(
            MONEY_SCALE,
            RoundingMode.HALF_UP
        );
    }
}