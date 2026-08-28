package com.sup2i.food.waste;

import com.sup2i.food.waste.util.WasteCostCalculator;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WasteCostCalculatorTest {

    @Test
    @DisplayName("Estimate multiplies quantity by unit cost")
    void estimateMultipliesQuantityByUnitCost() {
        BigDecimal estimate =
            WasteCostCalculator.estimate(
                BigDecimal.valueOf(3.5),
                BigDecimal.valueOf(12.25)
            );

        assertThat(estimate)
            .isEqualByComparingTo(
                "42.88"
            );
    }

    @Test
    @DisplayName("Null quantity is treated as zero")
    void nullQuantityIsZero() {
        assertThat(
            WasteCostCalculator.estimate(
                null,
                BigDecimal.valueOf(5)
            )
        )
            .isEqualByComparingTo(
                "0.00"
            );
    }

    @Test
    @DisplayName("Null unit cost is treated as zero")
    void nullUnitCostIsZero() {
        assertThat(
            WasteCostCalculator.estimate(
                BigDecimal.valueOf(10),
                null
            )
        )
            .isEqualByComparingTo(
                "0.00"
            );
    }

    @Test
    @DisplayName("Result is rounded half up to two decimals")
    void resultRoundsHalfUp() {
        BigDecimal estimate =
            WasteCostCalculator.estimate(
                BigDecimal.ONE,
                BigDecimal.valueOf(5.005)
            );

        assertThat(estimate)
            .isEqualByComparingTo(
                "5.01"
            );

        BigDecimal estimateDown =
            WasteCostCalculator.estimate(
                BigDecimal.ONE,
                BigDecimal.valueOf(5.004)
            );

        assertThat(estimateDown)
            .isEqualByComparingTo(
                "5.00"
            );
    }

    @Test
    @DisplayName("Zero cost remains zero")
    void zeroCostRemainsZero() {
        assertThat(
            WasteCostCalculator.estimate(
                BigDecimal.valueOf(8),
                BigDecimal.ZERO
            )
        )
            .isEqualByComparingTo(
                "0.00"
            );
    }
}