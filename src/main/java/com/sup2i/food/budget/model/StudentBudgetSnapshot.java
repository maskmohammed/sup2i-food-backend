package com.sup2i.food.budget.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record StudentBudgetSnapshot(
    UUID studentId,
    YearMonth month,
    LocalDate periodStart,
    LocalDate periodEnd,
    String campusTimezone,
    BudgetAssessment budget,
    List<SpendByCurrency> spendByCurrency,
    List<SubscriptionComparison> subscriptions
) {

    public StudentBudgetSnapshot {

        spendByCurrency =
            spendByCurrency == null
                ? List.of()
                : List.copyOf(
                    spendByCurrency
                );

        subscriptions =
            subscriptions == null
                ? List.of()
                : List.copyOf(
                    subscriptions
                );
    }

    public record BudgetAssessment(
        BigDecimal monthlyBudget,
        String currency,
        BigDecimal alertThresholdPct,
        boolean enabled,
        BigDecimal netSpendInBudgetCurrency,
        BigDecimal usagePct,
        BigDecimal alertThresholdAmount,
        boolean alertThresholdReached,
        boolean otherCurrencySpendPresent,
        OffsetDateTime updatedAt
    ) {
    }

    public record SpendByCurrency(
        String currency,
        long paymentCount,
        BigDecimal grossPaid,
        BigDecimal completedRefunds,
        BigDecimal netSpend
    ) {
    }

    public record SubscriptionComparison(
        UUID subscriptionId,
        UUID planId,
        UUID planVersionId,
        String status,
        LocalDate startsAt,
        LocalDate endsAt,
        String billingPeriod,
        BigDecimal subscribedSnapshotPrice,
        BigDecimal administrativePaymentAmount,
        String paymentReference
    ) {
    }
}
