package com.sup2i.food.reporting.kpi.service;

import com.sup2i.food.reporting.kpi.api.dto.DocumentedKpiInput;
import com.sup2i.food.reporting.kpi.api.dto.DocumentedKpiResponse;
import com.sup2i.food.reporting.kpi.exception.ReportingKpiValidationException;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;

@Service
public class DocumentedKpiService {

    private static final MathContext CALCULATION_CONTEXT =
        MathContext.DECIMAL128;

    public DocumentedKpiResponse calculate(
        DocumentedKpiInput input
    ) {

        if (input == null) {

            throw new ReportingKpiValidationException(
                "KPI input is required."
            );
        }

        requireValue(
            input.snackRevenue(),
            "Snack revenue"
        );

        requireNonNegativeCount(
            input.paidSnackTransactions(),
            "Paid Snack transactions"
        );

        requireNonNegativeCount(
            input.mobilePreorderedSnackOrders(),
            "Mobile preordered Snack orders"
        );

        requireNonNegativeCount(
            input.totalSnackOrders(),
            "Total Snack orders"
        );

        requireNonNegativeCount(
            input.mealsDistributed(),
            "Meals distributed"
        );

        requireNonNegativeCount(
            input.availableMealRights(),
            "Available meal rights"
        );

        requireNonNegative(
            input.wastedQuantity(),
            "Wasted quantity"
        );

        requireNonNegative(
            input.preparedQuantity(),
            "Prepared quantity"
        );

        requireNonNegative(
            input.estimatedWasteCost(),
            "Estimated waste cost"
        );

        requireNonNegative(
            input.consumedMaterialCost(),
            "Consumed material cost"
        );

        requireValue(
            input.totalRevenue(),
            "Total revenue"
        );

        BigDecimal averageBasket =
            ratio(
                input.snackRevenue(),
                input.paidSnackTransactions()
            );

        BigDecimal preorderRate =
            ratio(
                BigDecimal.valueOf(
                    input.mobilePreorderedSnackOrders()
                ),
                input.totalSnackOrders()
            );

        BigDecimal canteenUsageRate =
            ratio(
                BigDecimal.valueOf(
                    input.mealsDistributed()
                ),
                input.availableMealRights()
            );

        BigDecimal wasteQuantityRate =
            ratio(
                input.wastedQuantity(),
                input.preparedQuantity()
            );

        BigDecimal wasteValueRate =
            ratio(
                input.estimatedWasteCost(),
                input.consumedMaterialCost()
            );

        BigDecimal estimatedGrossMaterialMargin =
            input.totalRevenue()
                .subtract(
                    input.consumedMaterialCost(),
                    CALCULATION_CONTEXT
                );

        return new DocumentedKpiResponse(
            averageBasket,
            preorderRate,
            canteenUsageRate,
            wasteQuantityRate,
            wasteValueRate,
            estimatedGrossMaterialMargin
        );
    }

    private BigDecimal ratio(
        BigDecimal numerator,
        long denominator
    ) {

        if (denominator == 0L) {
            return null;
        }

        return numerator.divide(
            BigDecimal.valueOf(
                denominator
            ),
            CALCULATION_CONTEXT
        );
    }

    private BigDecimal ratio(
        BigDecimal numerator,
        BigDecimal denominator
    ) {

        if (
            denominator.compareTo(
                BigDecimal.ZERO
            ) == 0
        ) {
            return null;
        }

        return numerator.divide(
            denominator,
            CALCULATION_CONTEXT
        );
    }

    private void requireValue(
        BigDecimal value,
        String label
    ) {

        if (value == null) {

            throw new ReportingKpiValidationException(
                label + " is required."
            );
        }
    }

    private void requireNonNegative(
        BigDecimal value,
        String label
    ) {

        requireValue(
            value,
            label
        );

        if (
            value.compareTo(
                BigDecimal.ZERO
            ) < 0
        ) {

            throw new ReportingKpiValidationException(
                label + " must be greater than or equal to zero."
            );
        }
    }

    private void requireNonNegativeCount(
        long value,
        String label
    ) {

        if (value < 0L) {

            throw new ReportingKpiValidationException(
                label + " must be greater than or equal to zero."
            );
        }
    }
}