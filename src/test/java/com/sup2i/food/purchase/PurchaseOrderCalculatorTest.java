package com.sup2i.food.purchase;

import com.sup2i.food.purchase.domain.PurchaseOrderCalculator;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderCalculatorTest {

    @Test
    @DisplayName("Line total multiplies quantity by unit price")
    void lineTotalMultipliesQuantityByUnitPrice() {
        BigDecimal lineTotal =
            PurchaseOrderCalculator.lineTotal(
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(12.30)
            );

        assertThat(lineTotal)
            .isEqualByComparingTo(
                "36.90"
            );
    }

    @Test
    @DisplayName("Line total rounds half up to two decimals")
    void lineTotalRoundsHalfUp() {
        assertThat(
            PurchaseOrderCalculator.lineTotal(
                BigDecimal.valueOf(4.10),
                BigDecimal.valueOf(1.001)
            )
        )
            .isEqualByComparingTo(
                "4.10"
            );
    }

    @Test
    @DisplayName("Null unit price gives a null line total")
    void nullUnitPriceGivesNullLineTotal() {
        assertThat(
            PurchaseOrderCalculator.lineTotal(
                BigDecimal.valueOf(5),
                null
            )
        )
            .isNull();
    }

    @Test
    @DisplayName("Null quantity with a price gives zero")
    void nullQuantityGivesZeroLineTotal() {
        assertThat(
            PurchaseOrderCalculator.lineTotal(
                null,
                BigDecimal.valueOf(3)
            )
        )
            .isEqualByComparingTo(
                "0.00"
            );
    }

    @Test
    @DisplayName("Order total sums line totals")
    void orderTotalSumsLineTotals() {
        BigDecimal total =
            PurchaseOrderCalculator.orderTotal(
                List.of(
                    new BigDecimal("10.50"),
                    new BigDecimal("25.00"),
                    new BigDecimal("64.50")
                )
            );

        assertThat(total)
            .isEqualByComparingTo(
                "100.00"
            );
    }

    @Test
    @DisplayName("Order total ignores null line totals")
    void orderTotalIgnoresNullLineTotals() {
        BigDecimal total =
            PurchaseOrderCalculator.orderTotal(
                Arrays.asList(
                    new BigDecimal("10.00"),
                    null,
                    new BigDecimal("5.25")
                )
            );

        assertThat(total)
            .isEqualByComparingTo(
                "15.25"
            );
    }

    @Test
    @DisplayName("Order total of an empty order is zero")
    void orderTotalOfEmptyOrderIsZero() {
        assertThat(
            PurchaseOrderCalculator.orderTotal(
                List.of()
            )
        )
            .isEqualByComparingTo(
                "0.00"
        );
    }
}