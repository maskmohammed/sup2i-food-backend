package com.sup2i.food.canteen.service;

import com.sup2i.food.canteen.exception.CanteenErrorCode;
import com.sup2i.food.canteen.exception.CanteenException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class MealEligibilityService {

    private final JdbcTemplate jdbcTemplate;

    private final QuotaPeriodService
        quotaPeriodService;

    public MealEligibilityService(
        JdbcTemplate jdbcTemplate,
        QuotaPeriodService quotaPeriodService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.quotaPeriodService =
            quotaPeriodService;
    }

    /*
     * Reservation flow.
     *
     * The menu provides date and meal type.
     */
    @Transactional
    public EligibilityDecision requireStudentEligible(
        UUID organizationId,
        UUID studentId,
        UUID menuId
    ) {

        requireIdentifier(
            organizationId,
            "organizationId"
        );

        requireIdentifier(
            studentId,
            "studentId"
        );

        requireIdentifier(
            menuId,
            "menuId"
        );

        MenuContext menu =
            menuContext(
                organizationId,
                studentId,
                menuId
            );

        return evaluate(
            studentId,
            menu
        );
    }

    /*
     * Distribution flow.
     *
     * menuId is optional by OpenAPI contract.
     * When supplied, it must match the server-derived
     * usage date and requested meal type.
     */
    @Transactional
    public EligibilityDecision requireStudentEligible(
        UUID organizationId,
        UUID studentId,
        LocalDate usageDate,
        String mealType,
        UUID menuId
    ) {

        requireIdentifier(
            organizationId,
            "organizationId"
        );

        requireIdentifier(
            studentId,
            "studentId"
        );

        if (usageDate == null) {

            throw new IllegalArgumentException(
                "usageDate is required."
            );
        }

        if (
            mealType == null
            || mealType.isBlank()
        ) {

            throw new IllegalArgumentException(
                "mealType is required."
            );
        }

        MenuContext context;

        if (menuId == null) {

            requireStudentOrganization(
                organizationId,
                studentId
            );

            context =
                new MenuContext(
                    null,
                    usageDate,
                    mealType
                );
        }
        else {

            MenuContext persistedMenu =
                menuContext(
                    organizationId,
                    studentId,
                    menuId
                );

            boolean sameDate =
                usageDate.equals(
                    persistedMenu.date()
                );

            boolean sameMealType =
                mealType.equals(
                    persistedMenu.mealType()
                );

            if (
                !sameDate
                || !sameMealType
            ) {

                throw new CanteenException(
                    CanteenErrorCode.MEAL_NOT_ALLOWED,
                    "Menu does not match the current meal service."
                );
            }

            context =
                persistedMenu;
        }

        return evaluate(
            studentId,
            context
        );
    }

    private EligibilityDecision evaluate(
        UUID studentId,
        MenuContext menu
    ) {

        long activeSubscriptions =
            activeSubscriptionCount(
                studentId,
                menu.date()
            );

        if (activeSubscriptions == 0) {

            throw new CanteenException(
                CanteenErrorCode.SUBSCRIPTION_INACTIVE,
                "No active subscription covers this meal date."
            );
        }

        List<EntitlementRow> entitlements =
            lockEntitlements(
                studentId,
                menu
            );

        boolean mealTypeSeen =
            false;

        boolean validDateSeen =
            false;

        boolean mealRuleSeen =
            false;

        boolean quotaExhausted =
            false;

        boolean dailyLimitReached =
            false;

        for (
            EntitlementRow entitlement
                : entitlements
        ) {

            if (
                !menu.mealType().equals(
                    entitlement.mealType()
                )
            ) {
                continue;
            }

            mealTypeSeen =
                true;

            boolean validDate =
                !menu.date().isBefore(
                    entitlement.validFrom()
                )
                    && !menu.date().isAfter(
                        entitlement.validTo()
                    );

            if (!validDate) {
                continue;
            }

            validDateSeen =
                true;

            boolean allowed =
                entitlement.serviceAllowed()
                    && entitlement.entitlementDayAllowed()
                    && entitlement.planDayAllowed();

            if (!allowed) {
                continue;
            }

            mealRuleSeen =
                true;

            QuotaPeriodService.QuotaPeriod period =
                quotaPeriodService.resolve(
                    entitlement.quotaPeriodType(),
                    menu.date(),
                    entitlement.subscriptionStart(),
                    entitlement.subscriptionEnd(),
                    entitlement.validFrom(),
                    entitlement.validTo()
                );

            Integer baseQuota =
                entitlement.totalQuota();

            if (baseQuota == null) {

                baseQuota =
                    entitlement.planQuotaValue();
            }

            long adjustments =
                quotaAdjustments(
                    entitlement.entitlementId(),
                    period.start(),
                    menu.date()
                );

            Long effectiveQuota =
                null;

            long usedInPeriod =
                validUsageCount(
                    entitlement.entitlementId(),
                    period.start(),
                    period.end()
                );

            Long remainingQuota =
                null;

            if (baseQuota != null) {

                effectiveQuota =
                    (long) baseQuota
                        + adjustments;

                remainingQuota =
                    effectiveQuota
                        - usedInPeriod;

                if (remainingQuota <= 0) {

                    quotaExhausted =
                        true;

                    continue;
                }
            }

            int effectiveDailyLimit =
                effectiveDailyLimit(
                    entitlement.dailyLimit(),
                    entitlement.planMaxPerDay()
                );

            long usedToday =
                validUsageCount(
                    entitlement.entitlementId(),
                    menu.date(),
                    menu.date()
                );

            long remainingToday =
                (long) effectiveDailyLimit
                    - usedToday;

            if (remainingToday <= 0) {

                dailyLimitReached =
                    true;

                continue;
            }

            boolean reservationRequired =
                entitlement
                    .entitlementReservationRequired()
                    || entitlement
                        .planReservationRequired();

            return new EligibilityDecision(
                entitlement.subscriptionId(),
                entitlement.entitlementId(),
                entitlement.planId(),
                entitlement.planVersionId(),
                menu.id(),
                menu.date(),
                menu.mealType(),
                period.start(),
                period.end(),
                effectiveQuota,
                usedInPeriod,
                remainingQuota,
                effectiveDailyLimit,
                usedToday,
                remainingToday,
                reservationRequired
            );
        }

        if (dailyLimitReached) {

            throw new CanteenException(
                CanteenErrorCode.DAILY_LIMIT_REACHED,
                "Daily meal limit has been reached."
            );
        }

        if (quotaExhausted) {

            throw new CanteenException(
                CanteenErrorCode.QUOTA_EXHAUSTED,
                "Meal quota is exhausted."
            );
        }

        if (mealRuleSeen) {

            throw new IllegalStateException(
                "Eligibility evaluation reached an inconsistent state."
            );
        }

        if (validDateSeen) {

            throw new CanteenException(
                CanteenErrorCode.MEAL_NOT_ALLOWED,
                "Meal type or day is not covered by the subscription."
            );
        }

        if (mealTypeSeen) {

            throw new CanteenException(
                CanteenErrorCode.ENTITLEMENT_EXPIRED,
                "Meal entitlement is outside its validity period."
            );
        }

        throw new CanteenException(
            CanteenErrorCode.MEAL_NOT_ALLOWED,
            "Meal type is not covered by the subscription."
        );
    }

    private MenuContext menuContext(
        UUID organizationId,
        UUID studentId,
        UUID menuId
    ) {

        List<MenuContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    cm.id,
                    cm.menu_date,
                    cm.meal_type
                FROM canteen_menus cm
                JOIN locations l
                  ON l.id = cm.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                JOIN students s
                  ON s.id = ?
                 AND s.campus_id = c.id
                JOIN users u
                  ON u.id = s.user_id
                WHERE cm.id = ?
                  AND c.organization_id = ?
                  AND u.organization_id = ?
                  AND s.enrollment_status = 'ACTIVE'
                  AND u.status = 'ACTIVE'
                  AND c.is_active = TRUE
                  AND l.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new MenuContext(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "menu_date",
                            LocalDate.class
                        ),
                        resultSet.getString(
                            "meal_type"
                        )
                    ),
                studentId,
                menuId,
                organizationId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Canteen menu does not exist for this student campus."
            );
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Canteen menu eligibility lookup returned multiple rows."
            );
        }

        return rows.get(0);
    }

    private void requireStudentOrganization(
        UUID organizationId,
        UUID studentId
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM students s
                JOIN users u
                  ON u.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE s.id = ?
                  AND u.organization_id = ?
                  AND c.organization_id = ?
                """,
                Long.class,
                studentId,
                organizationId,
                organizationId
            );

        if (
            count == null
            || count != 1
        ) {

            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Student does not belong to this organization."
            );
        }
    }

    private long activeSubscriptionCount(
        UUID studentId,
        LocalDate mealDate
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM subscriptions s
                JOIN subscription_plan_versions spv
                  ON spv.id = s.plan_version_id
                 AND spv.plan_id = s.plan_id
                WHERE s.student_id = ?
                  AND s.meal_beneficiary_id IS NULL
                  AND s.status = 'ACTIVE'
                  AND ? BETWEEN s.starts_at
                            AND s.ends_at
                  AND spv.audience_type
                      IN ('STUDENT','ANY')
                """,
                Long.class,
                studentId,
                mealDate
            );

        if (count == null) {
            return 0;
        }

        return count;
    }

    private List<EntitlementRow> lockEntitlements(
        UUID studentId,
        MenuContext menu
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                s.id AS subscription_id,
                s.plan_id,
                s.plan_version_id,
                s.starts_at,
                s.ends_at,

                me.id AS entitlement_id,
                me.meal_type,
                me.valid_from,
                me.valid_to,
                me.total_quota,
                me.daily_limit,
                me.quota_period_type,
                me.reservation_required
                    AS entitlement_reservation_required,

                spv.quota_value
                    AS plan_quota_value,
                spv.max_per_day
                    AS plan_max_per_day,
                spv.reservation_required
                    AS plan_reservation_required,

                (
                    me.allowed_days IS NULL
                    OR ?::SMALLINT =
                        ANY(me.allowed_days)
                ) AS entitlement_day_allowed,

                (
                    spv.allowed_days IS NULL
                    OR ?::SMALLINT =
                        ANY(spv.allowed_days)
                ) AS plan_day_allowed,

                EXISTS (
                    SELECT 1
                    FROM subscription_plan_version_services spvs
                    WHERE spvs.plan_version_id =
                          s.plan_version_id
                      AND spvs.service_type =
                          ?
                ) AS service_allowed

            FROM subscriptions s

            JOIN subscription_plan_versions spv
              ON spv.id = s.plan_version_id
             AND spv.plan_id = s.plan_id

            JOIN meal_entitlements me
              ON me.subscription_id = s.id

            WHERE s.student_id = ?
              AND s.meal_beneficiary_id IS NULL
              AND s.status = 'ACTIVE'
              AND ? BETWEEN s.starts_at
                        AND s.ends_at
              AND spv.audience_type
                  IN ('STUDENT','ANY')

            ORDER BY
                me.valid_to ASC,
                s.ends_at ASC,
                me.id ASC

            FOR UPDATE OF s, me
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
                        "plan_id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "plan_version_id",
                        UUID.class
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
                    resultSet.getInt(
                        "daily_limit"
                    ),
                    resultSet.getString(
                        "quota_period_type"
                    ),
                    resultSet.getBoolean(
                        "entitlement_reservation_required"
                    ),
                    resultSet.getObject(
                        "plan_quota_value",
                        Integer.class
                    ),
                    resultSet.getObject(
                        "plan_max_per_day",
                        Integer.class
                    ),
                    resultSet.getBoolean(
                        "plan_reservation_required"
                    ),
                    resultSet.getBoolean(
                        "entitlement_day_allowed"
                    ),
                    resultSet.getBoolean(
                        "plan_day_allowed"
                    ),
                    resultSet.getBoolean(
                        "service_allowed"
                    )
                ),
            (short) menu.date()
                .getDayOfWeek()
                .getValue(),
            (short) menu.date()
                .getDayOfWeek()
                .getValue(),
            menu.mealType(),
            studentId,
            menu.date()
        );
    }

    private long quotaAdjustments(
        UUID entitlementId,
        LocalDate periodStart,
        LocalDate usageDate
    ) {

        Long total =
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    SUM(mea.quota_delta),
                    0
                )
                FROM meal_entitlement_adjustments mea
                WHERE mea.entitlement_id = ?
                  AND mea.effective_date
                      BETWEEN ? AND ?
                """,
                Long.class,
                entitlementId,
                periodStart,
                usageDate
            );

        if (total == null) {
            return 0;
        }

        return total;
    }

    private long validUsageCount(
        UUID entitlementId,
        LocalDate from,
        LocalDate to
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM meal_usages mu
                WHERE mu.entitlement_id = ?
                  AND mu.status = 'VALID'
                  AND mu.usage_date
                      BETWEEN ? AND ?
                """,
                Long.class,
                entitlementId,
                from,
                to
            );

        if (count == null) {
            return 0;
        }

        return count;
    }

    private int effectiveDailyLimit(
        int entitlementDailyLimit,
        Integer planMaxPerDay
    ) {

        if (entitlementDailyLimit <= 0) {

            throw new IllegalStateException(
                "Entitlement daily limit must be positive."
            );
        }

        if (planMaxPerDay == null) {
            return entitlementDailyLimit;
        }

        if (planMaxPerDay <= 0) {

            throw new IllegalStateException(
                "Plan daily limit must be positive."
            );
        }

        return Math.min(
            entitlementDailyLimit,
            planMaxPerDay
        );
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

    private record MenuContext(
        UUID id,
        LocalDate date,
        String mealType
    ) {
    }

    private record EntitlementRow(
        UUID subscriptionId,
        UUID planId,
        UUID planVersionId,
        LocalDate subscriptionStart,
        LocalDate subscriptionEnd,
        UUID entitlementId,
        String mealType,
        LocalDate validFrom,
        LocalDate validTo,
        Integer totalQuota,
        int dailyLimit,
        String quotaPeriodType,
        boolean entitlementReservationRequired,
        Integer planQuotaValue,
        Integer planMaxPerDay,
        boolean planReservationRequired,
        boolean entitlementDayAllowed,
        boolean planDayAllowed,
        boolean serviceAllowed
    ) {
    }

    public record EligibilityDecision(
        UUID subscriptionId,
        UUID entitlementId,
        UUID planId,
        UUID planVersionId,
        UUID menuId,
        LocalDate usageDate,
        String mealType,
        LocalDate quotaPeriodStart,
        LocalDate quotaPeriodEnd,
        Long totalQuota,
        long usedQuota,
        Long remainingQuota,
        int dailyLimit,
        long usedToday,
        long remainingToday,
        boolean reservationRequired
    ) {
    }
}
