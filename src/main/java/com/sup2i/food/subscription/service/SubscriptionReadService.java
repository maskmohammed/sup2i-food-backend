package com.sup2i.food.subscription.service;

import com.sup2i.food.canteen.service.QuotaPeriodService;
import com.sup2i.food.canteen.service.QuotaPeriodService.QuotaPeriod;
import com.sup2i.food.subscription.api.dto.SubscriptionEntitlementResponse;
import com.sup2i.food.subscription.api.dto.SubscriptionPlanResponse;
import com.sup2i.food.subscription.api.dto.SubscriptionResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SubscriptionReadService {

    private final JdbcTemplate jdbcTemplate;

    private final QuotaPeriodService quotaPeriodService;

    public SubscriptionReadService(
        JdbcTemplate jdbcTemplate,
        QuotaPeriodService quotaPeriodService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.quotaPeriodService =
            quotaPeriodService;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> mine(
        UUID actorId
    ) {

        StudentContext student =
            studentContext(
                actorId
            );

        List<SubscriptionRow> subscriptions =
            subscriptions(
                student
            );

        if (subscriptions.isEmpty()) {
            return List.of();
        }

        List<EntitlementRow> entitlements =
            entitlements(
                student
            );

        List<UsageRow> usages =
            validUsages(
                student
            );

        List<AdjustmentRow> adjustments =
            adjustments(
                student
            );

        Map<UUID, List<EntitlementRow>>
            entitlementsBySubscription =
                groupEntitlements(
                    entitlements
                );

        Map<UUID, List<UsageRow>>
            usagesByEntitlement =
                groupUsages(
                    usages
                );

        Map<UUID, List<AdjustmentRow>>
            adjustmentsByEntitlement =
                groupAdjustments(
                    adjustments
                );

        List<SubscriptionResponse> responses =
            new ArrayList<>();

        for (
            SubscriptionRow subscription
                : subscriptions
        ) {

            List<SubscriptionEntitlementResponse>
                entitlementResponses =
                    new ArrayList<>();

            List<EntitlementRow>
                subscriptionEntitlements =
                    entitlementsBySubscription
                        .getOrDefault(
                            subscription.id(),
                            List.of()
                        );

            for (
                EntitlementRow entitlement
                    : subscriptionEntitlements
            ) {

                entitlementResponses.add(
                    entitlementResponse(
                        student.localDate(),
                        subscription,
                        entitlement,
                        usagesByEntitlement
                            .getOrDefault(
                                entitlement.id(),
                                List.of()
                            ),
                        adjustmentsByEntitlement
                            .getOrDefault(
                                entitlement.id(),
                                List.of()
                            )
                    )
                );
            }

            SubscriptionPlanResponse plan =
                new SubscriptionPlanResponse(
                    subscription.planId(),
                    subscription.planName(),
                    subscription.planCode(),
                    subscription.billingPeriod(),
                    subscription.price(),
                    subscription.includedMeals(),
                    subscription.planActive()
                );

            responses.add(
                new SubscriptionResponse(
                    subscription.id(),
                    plan,
                    effectiveStatus(
                        subscription,
                        student.localDate()
                    ),
                    subscription.startsAt(),
                    subscription.endsAt(),
                    List.copyOf(
                        entitlementResponses
                    )
                )
            );
        }

        return List.copyOf(
            responses
        );
    }

    private StudentContext studentContext(
        UUID actorId
    ) {

        if (actorId == null) {

            throw new BadCredentialsException(
                "Authenticated user identifier is missing."
            );
        }

        List<StudentContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    student.id AS student_id,
                    user_account.organization_id,
                    (
                        CURRENT_TIMESTAMP
                        AT TIME ZONE campus.timezone
                    )::date AS local_date
                FROM users user_account
                JOIN organizations organization
                  ON organization.id =
                     user_account.organization_id
                JOIN students student
                  ON student.user_id =
                     user_account.id
                JOIN campuses campus
                  ON campus.id =
                     student.campus_id
                WHERE user_account.id = ?
                  AND user_account.status = 'ACTIVE'
                  AND organization.is_active = TRUE
                  AND student.enrollment_status = 'ACTIVE'
                  AND campus.is_active = TRUE
                  AND campus.organization_id =
                      user_account.organization_id
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
                        resultSet.getObject(
                            "organization_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "local_date",
                            LocalDate.class
                        )
                    ),
                actorId
            );

        if (rows.isEmpty()) {

            throw new BadCredentialsException(
                "Authenticated user is not an active student."
            );
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Student subscription context returned multiple rows."
            );
        }

        StudentContext context =
            rows.get(0);

        if (context.localDate() == null) {

            throw new IllegalStateException(
                "Student campus local date is unavailable."
            );
        }

        return context;
    }

    private List<SubscriptionRow> subscriptions(
        StudentContext student
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                subscription.id,
                subscription.status,
                subscription.starts_at,
                subscription.ends_at,

                plan.id AS plan_id,
                plan.name AS plan_name,
                plan.code AS plan_code,
                plan.is_active AS plan_active,

                plan_version.billing_period,
                plan_version.price,
                plan_version.included_meals

            FROM subscriptions subscription

            JOIN subscription_plans plan
              ON plan.id =
                 subscription.plan_id

            JOIN subscription_plan_versions
                 plan_version
              ON plan_version.id =
                 subscription.plan_version_id
             AND plan_version.plan_id =
                 subscription.plan_id

            JOIN students student
              ON student.id =
                 subscription.student_id

            JOIN users student_user
              ON student_user.id =
                 student.user_id

            JOIN campuses campus
              ON campus.id =
                 student.campus_id

            WHERE subscription.student_id = ?
              AND subscription.meal_beneficiary_id
                  IS NULL
              AND plan.organization_id = ?
              AND student_user.organization_id = ?
              AND campus.organization_id = ?
              AND plan_version.audience_type
                  IN (
                      'STUDENT',
                      'ANY'
                  )

            ORDER BY
                subscription.starts_at DESC,
                subscription.created_at DESC,
                subscription.id DESC
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new SubscriptionRow(
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getObject(
                        "starts_at",
                        LocalDate.class
                    ),
                    resultSet.getObject(
                        "ends_at",
                        LocalDate.class
                    ),
                    resultSet.getObject(
                        "plan_id",
                        UUID.class
                    ),
                    resultSet.getString(
                        "plan_name"
                    ),
                    resultSet.getString(
                        "plan_code"
                    ),
                    resultSet.getBoolean(
                        "plan_active"
                    ),
                    resultSet.getString(
                        "billing_period"
                    ),
                    resultSet.getBigDecimal(
                        "price"
                    ),
                    resultSet.getObject(
                        "included_meals",
                        Integer.class
                    )
                ),
            student.studentId(),
            student.organizationId(),
            student.organizationId(),
            student.organizationId()
        );
    }

    private List<EntitlementRow> entitlements(
        StudentContext student
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                subscription.id
                    AS subscription_id,

                entitlement.id
                    AS entitlement_id,

                entitlement.meal_type,
                entitlement.valid_from,
                entitlement.valid_to,
                entitlement.total_quota,
                entitlement.quota_period_type,

                plan_version.quota_value
                    AS plan_quota_value

            FROM subscriptions subscription

            JOIN subscription_plans plan
              ON plan.id =
                 subscription.plan_id

            JOIN subscription_plan_versions
                 plan_version
              ON plan_version.id =
                 subscription.plan_version_id
             AND plan_version.plan_id =
                 subscription.plan_id

            JOIN meal_entitlements entitlement
              ON entitlement.subscription_id =
                 subscription.id

            WHERE subscription.student_id = ?
              AND subscription.meal_beneficiary_id
                  IS NULL
              AND plan.organization_id = ?
              AND plan_version.audience_type
                  IN (
                      'STUDENT',
                      'ANY'
                  )

            ORDER BY
                subscription.starts_at DESC,
                entitlement.valid_from ASC,
                entitlement.meal_type ASC,
                entitlement.id ASC
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new EntitlementRow(
                    resultSet.getObject(
                        "subscription_id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "entitlement_id",
                        UUID.class
                    ),
                    resultSet.getString(
                        "meal_type"
                    ),
                    resultSet.getObject(
                        "valid_from",
                        LocalDate.class
                    ),
                    resultSet.getObject(
                        "valid_to",
                        LocalDate.class
                    ),
                    resultSet.getObject(
                        "total_quota",
                        Integer.class
                    ),
                    resultSet.getString(
                        "quota_period_type"
                    ),
                    resultSet.getObject(
                        "plan_quota_value",
                        Integer.class
                    )
                ),
            student.studentId(),
            student.organizationId()
        );
    }

    private List<UsageRow> validUsages(
        StudentContext student
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                usage.entitlement_id,
                usage.usage_date

            FROM meal_usages usage

            JOIN meal_entitlements entitlement
              ON entitlement.id =
                 usage.entitlement_id

            JOIN subscriptions subscription
              ON subscription.id =
                 entitlement.subscription_id

            JOIN subscription_plans plan
              ON plan.id =
                 subscription.plan_id

            WHERE subscription.student_id = ?
              AND subscription.meal_beneficiary_id
                  IS NULL
              AND usage.student_id = ?
              AND usage.meal_beneficiary_id
                  IS NULL
              AND usage.status = 'VALID'
              AND plan.organization_id = ?

            ORDER BY
                usage.entitlement_id ASC,
                usage.usage_date ASC,
                usage.id ASC
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new UsageRow(
                    resultSet.getObject(
                        "entitlement_id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "usage_date",
                        LocalDate.class
                    )
                ),
            student.studentId(),
            student.studentId(),
            student.organizationId()
        );
    }

    private List<AdjustmentRow> adjustments(
        StudentContext student
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                adjustment.entitlement_id,
                adjustment.effective_date,
                adjustment.quota_delta

            FROM meal_entitlement_adjustments
                 adjustment

            JOIN meal_entitlements entitlement
              ON entitlement.id =
                 adjustment.entitlement_id

            JOIN subscriptions subscription
              ON subscription.id =
                 entitlement.subscription_id

            JOIN subscription_plans plan
              ON plan.id =
                 subscription.plan_id

            WHERE subscription.student_id = ?
              AND subscription.meal_beneficiary_id
                  IS NULL
              AND plan.organization_id = ?

            ORDER BY
                adjustment.entitlement_id ASC,
                adjustment.effective_date ASC,
                adjustment.created_at ASC,
                adjustment.id ASC
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new AdjustmentRow(
                    resultSet.getObject(
                        "entitlement_id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "effective_date",
                        LocalDate.class
                    ),
                    resultSet.getInt(
                        "quota_delta"
                    )
                ),
            student.studentId(),
            student.organizationId()
        );
    }

    private SubscriptionEntitlementResponse
        entitlementResponse(
            LocalDate localDate,
            SubscriptionRow subscription,
            EntitlementRow entitlement,
            List<UsageRow> usages,
            List<AdjustmentRow> adjustments
        ) {

        LocalDate effectiveStart =
            max(
                subscription.startsAt(),
                entitlement.validFrom()
            );

        LocalDate effectiveEnd =
            min(
                subscription.endsAt(),
                entitlement.validTo()
            );

        if (
            effectiveEnd.isBefore(
                effectiveStart
            )
        ) {

            throw new IllegalStateException(
                "Subscription entitlement validity does not overlap subscription validity."
            );
        }

        LocalDate referenceDate =
            clamp(
                localDate,
                effectiveStart,
                effectiveEnd
            );

        QuotaPeriod period =
            quotaPeriodService.resolve(
                entitlement.quotaPeriodType(),
                referenceDate,
                subscription.startsAt(),
                subscription.endsAt(),
                entitlement.validFrom(),
                entitlement.validTo()
            );

        long usedQuota =
            usages.stream()
                .filter(
                    usage ->
                        !usage.usageDate()
                            .isBefore(
                                period.start()
                            )
                            && !usage.usageDate()
                                .isAfter(
                                    period.end()
                                )
                )
                .count();

        Integer baseQuota =
            entitlement.totalQuota();

        if (baseQuota == null) {

            baseQuota =
                entitlement.planQuotaValue();
        }

        Long effectiveQuota =
            null;

        Long remainingQuota =
            null;

        if (baseQuota != null) {

            long adjustmentTotal =
                adjustments.stream()
                    .filter(
                        adjustment ->
                            !adjustment
                                .effectiveDate()
                                .isBefore(
                                    period.start()
                                )
                                && !adjustment
                                    .effectiveDate()
                                    .isAfter(
                                        referenceDate
                                    )
                    )
                    .mapToLong(
                        AdjustmentRow::quotaDelta
                    )
                    .sum();

            long calculatedQuota =
                (long) baseQuota
                    + adjustmentTotal;

            effectiveQuota =
                Math.max(
                    0L,
                    calculatedQuota
                );

            remainingQuota =
                Math.max(
                    0L,
                    effectiveQuota
                        - usedQuota
                );
        }

        return new SubscriptionEntitlementResponse(
            entitlement.mealType(),
            effectiveQuota,
            usedQuota,
            remainingQuota
        );
    }

    private Map<UUID, List<EntitlementRow>>
        groupEntitlements(
            List<EntitlementRow> rows
        ) {

        Map<UUID, List<EntitlementRow>> result =
            new LinkedHashMap<>();

        for (EntitlementRow row : rows) {

            result.computeIfAbsent(
                row.subscriptionId(),
                ignored ->
                    new ArrayList<>()
            )
                .add(
                    row
                );
        }

        return result;
    }

    private Map<UUID, List<UsageRow>>
        groupUsages(
            List<UsageRow> rows
        ) {

        Map<UUID, List<UsageRow>> result =
            new LinkedHashMap<>();

        for (UsageRow row : rows) {

            result.computeIfAbsent(
                row.entitlementId(),
                ignored ->
                    new ArrayList<>()
            )
                .add(
                    row
                );
        }

        return result;
    }

    private Map<UUID, List<AdjustmentRow>>
        groupAdjustments(
            List<AdjustmentRow> rows
        ) {

        Map<UUID, List<AdjustmentRow>> result =
            new LinkedHashMap<>();

        for (AdjustmentRow row : rows) {

            result.computeIfAbsent(
                row.entitlementId(),
                ignored ->
                    new ArrayList<>()
            )
                .add(
                    row
                );
        }

        return result;
    }

    private String effectiveStatus(
        SubscriptionRow subscription,
        LocalDate localDate
    ) {

        if (
            "ACTIVE".equals(
                subscription.status()
            )
            && subscription.endsAt()
                .isBefore(
                    localDate
                )
        ) {

            return "EXPIRED";
        }

        return subscription.status();
    }

    private LocalDate clamp(
        LocalDate value,
        LocalDate minimum,
        LocalDate maximum
    ) {

        if (value.isBefore(minimum)) {
            return minimum;
        }

        if (value.isAfter(maximum)) {
            return maximum;
        }

        return value;
    }

    private LocalDate max(
        LocalDate first,
        LocalDate second
    ) {

        if (first.isAfter(second)) {
            return first;
        }

        return second;
    }

    private LocalDate min(
        LocalDate first,
        LocalDate second
    ) {

        if (first.isBefore(second)) {
            return first;
        }

        return second;
    }

    private record StudentContext(
        UUID studentId,
        UUID organizationId,
        LocalDate localDate
    ) {
    }

    private record SubscriptionRow(
        UUID id,
        String status,
        LocalDate startsAt,
        LocalDate endsAt,
        UUID planId,
        String planName,
        String planCode,
        boolean planActive,
        String billingPeriod,
        BigDecimal price,
        Integer includedMeals
    ) {
    }

    private record EntitlementRow(
        UUID subscriptionId,
        UUID id,
        String mealType,
        LocalDate validFrom,
        LocalDate validTo,
        Integer totalQuota,
        String quotaPeriodType,
        Integer planQuotaValue
    ) {
    }

    private record UsageRow(
        UUID entitlementId,
        LocalDate usageDate
    ) {
    }

    private record AdjustmentRow(
        UUID entitlementId,
        LocalDate effectiveDate,
        int quotaDelta
    ) {
    }
}