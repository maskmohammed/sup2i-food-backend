package com.sup2i.food.budget.service;

import com.sup2i.food.budget.model.StudentBudgetSnapshot;
import com.sup2i.food.budget.model.StudentBudgetSnapshot.BudgetAssessment;
import com.sup2i.food.budget.model.StudentBudgetSnapshot.SpendByCurrency;
import com.sup2i.food.budget.model.StudentBudgetSnapshot.SubscriptionComparison;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StudentBudgetService {

    private static final BigDecimal
        ONE_HUNDRED =
            new BigDecimal("100");

    private final JdbcTemplate jdbcTemplate;

    public StudentBudgetService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    /*
     * Internal read model only.
     *
     * This service intentionally has no controller/API contract.
     * It does not activate or mutate Wallet.
     *
     * Monthly spend is derived from persisted payment truth:
     *
     * - payment belongs to an order owned by the student;
     * - payment.paid_at is inside the selected campus-local month;
     * - payment is COMPLETED, PARTIALLY_REFUNDED or REFUNDED;
     * - only COMPLETED refunds linked to those payments reduce spend;
     * - currencies are never converted or merged.
     *
     * Subscription comparison is factual only:
     *
     * - snapshot price comes from subscription_plan_versions;
     * - administrative_payment_amount is returned separately;
     * - no monthly allocation is invented;
     * - no savings claim is calculated.
     */
    @Transactional(readOnly = true)
    public Optional<StudentBudgetSnapshot> read(
        UUID organizationId,
        UUID studentId,
        YearMonth requestedMonth
    ) {

        requireIdentifier(
            organizationId,
            "organizationId"
        );

        requireIdentifier(
            studentId,
            "studentId"
        );

        Optional<StudentContext> context =
            studentContext(
                organizationId,
                studentId
            );

        if (context.isEmpty()) {
            return Optional.empty();
        }

        StudentContext student =
            context.get();

        ZoneId campusZone =
            zoneId(
                student.campusTimezone()
            );

        YearMonth month =
            requestedMonth;

        if (month == null) {

            month =
                YearMonth.now(
                    campusZone
                );
        }

        LocalDate periodStart =
            month.atDay(1);

        LocalDate periodEnd =
            month.atEndOfMonth();

        OffsetDateTime startInclusive =
            periodStart
                .atStartOfDay(
                    campusZone
                )
                .toOffsetDateTime();

        OffsetDateTime endExclusive =
            month
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(
                    campusZone
                )
                .toOffsetDateTime();

        List<SpendByCurrency> spend =
            spendByCurrency(
                organizationId,
                studentId,
                startInclusive,
                endExclusive
            );

        Optional<BudgetSettingsRow> settings =
            budgetSettings(
                studentId
            );

        BudgetAssessment assessment =
            settings
                .map(current ->
                    assessBudget(
                        current,
                        spend
                    )
                )
                .orElse(null);

        List<SubscriptionComparison>
            subscriptions =
                subscriptionComparisons(
                    organizationId,
                    studentId,
                    periodStart,
                    periodEnd
                );

        return Optional.of(
            new StudentBudgetSnapshot(
                studentId,
                month,
                periodStart,
                periodEnd,
                student.campusTimezone(),
                assessment,
                spend,
                subscriptions
            )
        );
    }

    private Optional<StudentContext> studentContext(
        UUID organizationId,
        UUID studentId
    ) {

        List<StudentContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    s.id AS student_id,
                    c.timezone AS campus_timezone
                FROM students s
                JOIN users u
                  ON u.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE s.id = ?
                  AND u.organization_id = ?
                  AND c.organization_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new StudentContext(
                        resultSet.getObject(
                            "student_id",
                            UUID.class
                        ),
                        requiredText(
                            resultSet.getString(
                                "campus_timezone"
                            ),
                            "Student campus timezone"
                        )
                    ),
                studentId,
                organizationId,
                organizationId
            );

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Student budget tenant lookup returned multiple rows."
            );
        }

        return Optional.of(
            rows.get(0)
        );
    }

    private Optional<BudgetSettingsRow> budgetSettings(
        UUID studentId
    ) {

        List<BudgetSettingsRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    monthly_budget,
                    currency,
                    alert_threshold_pct,
                    is_enabled,
                    updated_at
                FROM student_budget_settings
                WHERE student_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new BudgetSettingsRow(
                        money(
                            resultSet.getBigDecimal(
                                "monthly_budget"
                            )
                        ),
                        currency(
                            resultSet.getString(
                                "currency"
                            )
                        ),
                        percentage(
                            resultSet.getBigDecimal(
                                "alert_threshold_pct"
                            )
                        ),
                        resultSet.getBoolean(
                            "is_enabled"
                        ),
                        resultSet.getObject(
                            "updated_at",
                            OffsetDateTime.class
                        )
                    ),
                studentId
            );

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Student budget settings lookup returned multiple rows."
            );
        }

        return Optional.of(
            rows.get(0)
        );
    }

    private List<SpendByCurrency> spendByCurrency(
        UUID organizationId,
        UUID studentId,
        OffsetDateTime startInclusive,
        OffsetDateTime endExclusive
    ) {

        return jdbcTemplate.query(
            """
            WITH period_payments AS (
                SELECT
                    p.id,
                    p.currency,
                    p.amount
                FROM payments p
                JOIN orders o
                  ON o.id = p.order_id
                WHERE o.organization_id = ?
                  AND o.student_id = ?
                  AND p.paid_at IS NOT NULL
                  AND p.paid_at >= ?
                  AND p.paid_at < ?
                  AND p.status IN (
                      'COMPLETED',
                      'PARTIALLY_REFUNDED',
                      'REFUNDED'
                  )
            ),
            completed_refunds AS (
                SELECT
                    pp.currency,
                    SUM(r.amount)
                        AS completed_refunds
                FROM period_payments pp
                JOIN refunds r
                  ON r.payment_id = pp.id
                WHERE r.status = 'COMPLETED'
                GROUP BY pp.currency
            )
            SELECT
                pp.currency,
                COUNT(*) AS payment_count,
                COALESCE(
                    SUM(pp.amount),
                    0
                ) AS gross_paid,
                COALESCE(
                    MAX(cr.completed_refunds),
                    0
                ) AS completed_refunds
            FROM period_payments pp
            LEFT JOIN completed_refunds cr
              ON cr.currency = pp.currency
            GROUP BY pp.currency
            ORDER BY pp.currency
            """,
            (
                resultSet,
                rowNumber
            ) -> {

                BigDecimal gross =
                    money(
                        resultSet.getBigDecimal(
                            "gross_paid"
                        )
                    );

                BigDecimal refunds =
                    money(
                        resultSet.getBigDecimal(
                            "completed_refunds"
                        )
                    );

                BigDecimal net =
                    money(
                        gross.subtract(
                            refunds
                        )
                    );

                return new SpendByCurrency(
                    currency(
                        resultSet.getString(
                            "currency"
                        )
                    ),
                    resultSet.getLong(
                        "payment_count"
                    ),
                    gross,
                    refunds,
                    net
                );
            },
            organizationId,
            studentId,
            startInclusive,
            endExclusive
        );
    }

    private BudgetAssessment assessBudget(
        BudgetSettingsRow settings,
        List<SpendByCurrency> spend
    ) {

        BigDecimal netSpend =
            zeroMoney();

        boolean matchingCurrencyFound =
            false;

        boolean otherCurrencySpendPresent =
            false;

        for (
            SpendByCurrency current
                : spend
        ) {

            if (
                settings
                    .currency()
                    .equals(
                        current.currency()
                    )
            ) {

                if (matchingCurrencyFound) {

                    throw new IllegalStateException(
                        "Student spend contains duplicate currency groups."
                    );
                }

                matchingCurrencyFound =
                    true;

                netSpend =
                    current.netSpend();
            }
            else {

                otherCurrencySpendPresent =
                    true;
            }
        }

        BigDecimal thresholdAmount =
            money(
                settings
                    .monthlyBudget()
                    .multiply(
                        settings
                            .alertThresholdPct()
                    )
                    .divide(
                        ONE_HUNDRED,
                        2,
                        RoundingMode.HALF_UP
                    )
            );

        BigDecimal usagePct =
            netSpend
                .multiply(
                    ONE_HUNDRED
                )
                .divide(
                    settings.monthlyBudget(),
                    2,
                    RoundingMode.HALF_UP
                );

        boolean thresholdReached =
            settings.enabled()
                && netSpend.compareTo(
                    thresholdAmount
                ) >= 0;

        return new BudgetAssessment(
            settings.monthlyBudget(),
            settings.currency(),
            settings.alertThresholdPct(),
            settings.enabled(),
            netSpend,
            usagePct,
            thresholdAmount,
            thresholdReached,
            otherCurrencySpendPresent,
            settings.updatedAt()
        );
    }

    private List<SubscriptionComparison>
        subscriptionComparisons(
            UUID organizationId,
            UUID studentId,
            LocalDate periodStart,
            LocalDate periodEnd
        ) {

        return jdbcTemplate.query(
            """
            SELECT
                s.id AS subscription_id,
                s.plan_id,
                s.plan_version_id,
                s.status,
                s.starts_at,
                s.ends_at,
                s.administrative_payment_amount,
                s.payment_reference,

                spv.billing_period,
                spv.price AS subscribed_snapshot_price

            FROM subscriptions s

            JOIN subscription_plans sp
              ON sp.id = s.plan_id
             AND sp.organization_id = ?

            JOIN subscription_plan_versions spv
              ON spv.id = s.plan_version_id
             AND spv.plan_id = s.plan_id

            WHERE s.student_id = ?
              AND s.meal_beneficiary_id IS NULL
              AND s.starts_at <= ?
              AND s.ends_at >= ?

            ORDER BY
                s.starts_at ASC,
                s.id ASC
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new SubscriptionComparison(
                    resultSet.getObject(
                        "subscription_id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "plan_id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "plan_version_id",
                        UUID.class
                    ),
                    requiredText(
                        resultSet.getString(
                            "status"
                        ),
                        "Subscription status"
                    ),
                    resultSet.getObject(
                        "starts_at",
                        LocalDate.class
                    ),
                    resultSet.getObject(
                        "ends_at",
                        LocalDate.class
                    ),
                    requiredText(
                        resultSet.getString(
                            "billing_period"
                        ),
                        "Subscription billing period"
                    ),
                    money(
                        resultSet.getBigDecimal(
                            "subscribed_snapshot_price"
                        )
                    ),
                    nullableMoney(
                        resultSet.getBigDecimal(
                            "administrative_payment_amount"
                        )
                    ),
                    nullableText(
                        resultSet.getString(
                            "payment_reference"
                        )
                    )
                ),
            organizationId,
            studentId,
            periodEnd,
            periodStart
        );
    }

    private ZoneId zoneId(
        String timezone
    ) {

        try {

            return ZoneId.of(
                requiredText(
                    timezone,
                    "Campus timezone"
                )
            );
        }
        catch (DateTimeException exception) {

            throw new IllegalStateException(
                "Campus timezone is invalid.",
                exception
            );
        }
    }

    private BigDecimal money(
        BigDecimal value
    ) {

        if (value == null) {

            throw new IllegalStateException(
                "Required monetary value is missing."
            );
        }

        return value.setScale(
            2,
            RoundingMode.UNNECESSARY
        );
    }

    private BigDecimal nullableMoney(
        BigDecimal value
    ) {

        if (value == null) {
            return null;
        }

        return money(
            value
        );
    }

    private BigDecimal percentage(
        BigDecimal value
    ) {

        if (value == null) {

            throw new IllegalStateException(
                "Required percentage value is missing."
            );
        }

        return value.setScale(
            2,
            RoundingMode.UNNECESSARY
        );
    }

    private BigDecimal zeroMoney() {

        return BigDecimal.ZERO
            .setScale(2);
    }

    private String currency(
        String value
    ) {

        String normalized =
            requiredText(
                value,
                "Currency"
            );

        if (normalized.length() != 3) {

            throw new IllegalStateException(
                "Currency must contain exactly three characters."
            );
        }

        return normalized;
    }

    private String requiredText(
        String value,
        String label
    ) {

        if (
            value == null
            || value.isBlank()
        ) {

            throw new IllegalStateException(
                label + " is missing."
            );
        }

        return value.trim();
    }

    private String nullableText(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }

    private void requireIdentifier(
        UUID value,
        String label
    ) {

        if (value == null) {

            throw new IllegalArgumentException(
                label + " is required."
            );
        }
    }

    private record StudentContext(
        UUID studentId,
        String campusTimezone
    ) {
    }

    private record BudgetSettingsRow(
        BigDecimal monthlyBudget,
        String currency,
        BigDecimal alertThresholdPct,
        boolean enabled,
        OffsetDateTime updatedAt
    ) {
    }
}
