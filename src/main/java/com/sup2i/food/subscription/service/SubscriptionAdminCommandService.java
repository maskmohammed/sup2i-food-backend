package com.sup2i.food.subscription.service;

import com.sup2i.food.subscription.api.dto.AdminSubscriptionPlanResponse;
import com.sup2i.food.subscription.api.dto.AdminSubscriptionResponse;
import com.sup2i.food.subscription.api.dto.CreateSubscriptionCommand;
import com.sup2i.food.subscription.api.dto.CreateSubscriptionPlanCommand;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SubscriptionAdminCommandService {

    private static final Set<String> BILLING_PERIODS =
        Set.of(
            "WEEK",
            "MONTH",
            "SEMESTER",
            "SCHOOL_YEAR",
            "MEAL_PACK",
            "CUSTOM"
        );

    private static final Set<String> QUOTA_PERIODS =
        Set.of(
            "SUBSCRIPTION",
            "WEEK",
            "MONTH",
            "DAY"
        );

    private static final Set<String> SERVICES =
        Set.of(
            "BREAKFAST",
            "LUNCH",
            "DINNER",
            "OTHER"
        );

    private static final Set<String> RENEWAL_POLICIES =
        Set.of(
            "MANUAL",
            "AUTO"
        );

    private static final Set<String> SUSPENSION_POLICIES =
        Set.of(
            "BLOCK_USAGE",
            "PAUSE_VALIDITY",
            "CUSTOM"
        );

    private final JdbcTemplate jdbcTemplate;

    private final JsonMapper jsonMapper;

    public SubscriptionAdminCommandService(
        JdbcTemplate jdbcTemplate,
        JsonMapper jsonMapper
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.jsonMapper =
            jsonMapper;
    }

    @Transactional
    public AdminSubscriptionPlanResponse createPlan(
        UUID actorId,
        CreateSubscriptionPlanCommand command
    ) {
        if (command == null) {
            throw badRequest(
                "Subscription plan request is required."
            );
        }

        UUID organizationId =
            organizationId(
                actorId
            );

        String name =
            required(
                command.name(),
                "name"
            );

        String code =
            required(
                command.code(),
                "code"
            );

        String audienceType =
            upper(
                command.audienceType(),
                "audienceType"
            );

        if (!"STUDENT".equals(audienceType)) {
            throw badRequest(
                "Only STUDENT subscription plans are supported by the MVP endpoint."
            );
        }

        String billingPeriod =
            allowed(
                command.billingPeriod(),
                "billingPeriod",
                BILLING_PERIODS
            );

        String quotaPeriodType =
            allowed(
                command.quotaPeriodType(),
                "quotaPeriodType",
                QUOTA_PERIODS
            );

        String renewalPolicy =
            allowed(
                command.renewalPolicy(),
                "renewalPolicy",
                RENEWAL_POLICIES
            );

        String suspensionPolicy =
            allowed(
                command.suspensionPolicy(),
                "suspensionPolicy",
                SUSPENSION_POLICIES
            );

        validateNonNegative(
            command.price(),
            "price"
        );

        validatePositive(
            command.includedMeals(),
            "includedMeals"
        );

        validatePositive(
            command.validityDays(),
            "validityDays"
        );

        validatePositive(
            command.quotaValue(),
            "quotaValue"
        );

        if (
            command.maxPerDay() == null
            || command.maxPerDay() <= 0
        ) {
            throw badRequest(
                "maxPerDay is required and must be positive."
            );
        }

        List<Integer> allowedDays =
            normalizeDays(
                command.allowedDays()
            );

        List<String> services =
            normalizeServices(
                command.services()
            );

        if (command.reservationRequired() == null) {
            throw badRequest(
                "reservationRequired is required."
            );
        }

        if (command.rules() == null) {
            throw badRequest(
                "rules is required."
            );
        }

        boolean invalidSaleRange =
            command.saleStartsAt() != null
                && command.saleEndsAt() != null
                && !command.saleEndsAt().isAfter(
                    command.saleStartsAt()
                );

        if (invalidSaleRange) {
            throw badRequest(
                "saleEndsAt must be after saleStartsAt."
            );
        }

        validateAcademicCalendar(
            organizationId,
            command.academicCalendarId()
        );

        Long duplicate =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM subscription_plans
                WHERE organization_id = ?
                  AND UPPER(code) = UPPER(?)
                """,
                Long.class,
                organizationId,
                code
            );

        if (
            duplicate != null
            && duplicate > 0
        ) {
            throw conflict(
                "A subscription plan with this code already exists."
            );
        }

        String rulesJson =
            serializeRules(
                command.rules()
            );

        UUID planId =
            UUID.randomUUID();

        UUID versionId =
            UUID.randomUUID();

        OffsetDateTime now =
            OffsetDateTime.now();

        String allowedDaysSql =
            smallintArray(
                allowedDays
            );

        int planInserted =
            jdbcTemplate.update(
                """
                INSERT INTO subscription_plans (
                    id,
                    organization_id,
                    name,
                    code,
                    billing_period,
                    price,
                    included_meals,
                    validity_days,
                    quota_type,
                    quota_value,
                    max_per_day,
                    allowed_days,
                    reservation_required,
                    is_active,
                    rules,
                    academic_calendar_id,
                    quota_period_type,
                    renewal_policy,
                    suspension_policy,
                    reservation_deadline,
                    reservation_cancellation_deadline,
                    sale_starts_at,
                    sale_ends_at,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    CAST(? AS SMALLINT[]),
                    ?,
                    TRUE,
                    CAST(? AS jsonb),
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                planId,
                organizationId,
                name,
                code,
                billingPeriod,
                command.price(),
                command.includedMeals(),
                command.validityDays(),
                normalizeNullable(
                    command.quotaType()
                ),
                command.quotaValue(),
                command.maxPerDay(),
                allowedDaysSql,
                command.reservationRequired(),
                rulesJson,
                command.academicCalendarId(),
                quotaPeriodType,
                renewalPolicy,
                suspensionPolicy,
                command.reservationDeadline(),
                command.reservationCancellationDeadline(),
                command.saleStartsAt(),
                command.saleEndsAt(),
                now,
                now
            );

        if (planInserted != 1) {
            throw new IllegalStateException(
                "Subscription plan insert failed."
            );
        }

        for (String service : services) {
            int baseServiceInserted =
                jdbcTemplate.update(
                    """
                    INSERT INTO subscription_plan_services (
                        subscription_plan_id,
                        service_type
                    )
                    VALUES (
                        ?,
                        ?
                    )
                    """,
                    planId,
                    service
                );

            if (baseServiceInserted != 1) {
                throw new IllegalStateException(
                    "Subscription base plan service insert failed."
                );
            }
        }

        int versionInserted =
            jdbcTemplate.update(
                """
                INSERT INTO subscription_plan_versions (
                    id,
                    plan_id,
                    version_number,
                    audience_type,
                    billing_period,
                    price,
                    included_meals,
                    validity_days,
                    quota_type,
                    quota_period_type,
                    quota_value,
                    max_per_day,
                    allowed_days,
                    reservation_required,
                    reservation_deadline,
                    reservation_cancellation_deadline,
                    renewal_policy,
                    suspension_policy,
                    academic_calendar_id,
                    sale_starts_at,
                    sale_ends_at,
                    rules,
                    effective_from,
                    created_by,
                    created_at
                )
                VALUES (
                    ?,
                    ?,
                    1,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    CAST(? AS SMALLINT[]),
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    CAST(? AS jsonb),
                    ?,
                    ?,
                    ?
                )
                """,
                versionId,
                planId,
                audienceType,
                billingPeriod,
                command.price(),
                command.includedMeals(),
                command.validityDays(),
                normalizeNullable(
                    command.quotaType()
                ),
                quotaPeriodType,
                command.quotaValue(),
                command.maxPerDay(),
                allowedDaysSql,
                command.reservationRequired(),
                command.reservationDeadline(),
                command.reservationCancellationDeadline(),
                renewalPolicy,
                suspensionPolicy,
                command.academicCalendarId(),
                command.saleStartsAt(),
                command.saleEndsAt(),
                rulesJson,
                now,
                actorId,
                now
            );

        if (versionInserted != 1) {
            throw new IllegalStateException(
                "Subscription plan version insert failed."
            );
        }

        for (String service : services) {
            int serviceInserted =
                jdbcTemplate.update(
                    """
                    INSERT INTO subscription_plan_version_services (
                        plan_version_id,
                        service_type
                    )
                    VALUES (
                        ?,
                        ?
                    )
                    """,
                    versionId,
                    service
                );

            if (serviceInserted != 1) {
                throw new IllegalStateException(
                    "Subscription plan version service insert failed."
                );
            }
        }

        auditPlan(
            organizationId,
            actorId,
            planId,
            versionId,
            code
        );

        return new AdminSubscriptionPlanResponse(
            planId,
            versionId,
            1,
            name,
            code,
            audienceType,
            billingPeriod,
            command.price(),
            command.includedMeals(),
            command.validityDays(),
            normalizeNullable(
                command.quotaType()
            ),
            quotaPeriodType,
            command.quotaValue(),
            command.maxPerDay(),
            allowedDays,
            services,
            command.reservationRequired(),
            command.reservationDeadline(),
            command.reservationCancellationDeadline(),
            renewalPolicy,
            suspensionPolicy,
            command.academicCalendarId(),
            command.saleStartsAt(),
            command.saleEndsAt(),
            command.rules(),
            true
        );
    }

    @Transactional
    public AdminSubscriptionResponse createSubscription(
        UUID actorId,
        CreateSubscriptionCommand command
    ) {
        if (command == null) {
            throw badRequest(
                "Subscription request is required."
            );
        }

        boolean requiredMissing =
            command.studentId() == null
                || command.planVersionId() == null
                || command.startsAt() == null
                || command.endsAt() == null;

        if (requiredMissing) {
            throw badRequest(
                "studentId, planVersionId, startsAt and endsAt are required."
            );
        }

        if (
            command.endsAt()
                .isBefore(
                    command.startsAt()
                )
        ) {
            throw badRequest(
                "endsAt cannot be before startsAt."
            );
        }

        UUID organizationId =
            organizationId(
                actorId
            );

        requireStudentOwned(
            organizationId,
            command.studentId()
        );

        VersionRow version =
            version(
                organizationId,
                command.planVersionId()
            );

        if (!"STUDENT".equals(version.audienceType())) {
            throw badRequest(
                "Only STUDENT plan versions are supported by the MVP endpoint."
            );
        }

        if (
            version.maxPerDay() == null
            || version.maxPerDay() <= 0
        ) {
            throw conflict(
                "Selected plan version has no explicit maxPerDay snapshot."
            );
        }

        List<String> services =
            versionServices(
                version.id()
            );

        if (services.isEmpty()) {
            throw conflict(
                "Selected plan version has no meal service snapshot."
            );
        }

        UUID subscriptionId =
            UUID.randomUUID();

        OffsetDateTime now =
            OffsetDateTime.now();

        int subscriptionInserted =
            jdbcTemplate.update(
                """
                INSERT INTO subscriptions (
                    id,
                    student_id,
                    meal_beneficiary_id,
                    plan_id,
                    plan_version_id,
                    status,
                    starts_at,
                    ends_at,
                    payment_reference,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?,
                    ?,
                    NULL,
                    ?,
                    ?,
                    'PENDING',
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                subscriptionId,
                command.studentId(),
                version.planId(),
                version.id(),
                command.startsAt(),
                command.endsAt(),
                normalizeNullable(
                    command.paymentReference()
                ),
                now,
                now
            );

        if (subscriptionInserted != 1) {
            throw new IllegalStateException(
                "Subscription insert failed."
            );
        }

        List<AdminSubscriptionResponse.EntitlementResponse> entitlements =
            new ArrayList<>();

        String days =
            smallintArray(
                version.allowedDays()
            );

        for (String service : services) {
            UUID entitlementId =
                UUID.randomUUID();

            int entitlementInserted =
                jdbcTemplate.update(
                    """
                    INSERT INTO meal_entitlements (
                        id,
                        subscription_id,
                        meal_type,
                        valid_from,
                        valid_to,
                        allowed_days,
                        total_quota,
                        daily_limit,
                        quota_period_type,
                        reservation_required,
                        created_at
                    )
                    VALUES (
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        CAST(? AS SMALLINT[]),
                        ?,
                        ?,
                        ?,
                        ?,
                        ?
                    )
                    """,
                    entitlementId,
                    subscriptionId,
                    service,
                    command.startsAt(),
                    command.endsAt(),
                    days,
                    version.quotaValue(),
                    version.maxPerDay(),
                    version.quotaPeriodType(),
                    version.reservationRequired(),
                    now
                );

            if (entitlementInserted != 1) {
                throw new IllegalStateException(
                    "Meal entitlement insert failed."
                );
            }

            entitlements.add(
                new AdminSubscriptionResponse.EntitlementResponse(
                    entitlementId,
                    service,
                    command.startsAt(),
                    command.endsAt(),
                    version.allowedDays(),
                    version.quotaValue(),
                    version.maxPerDay(),
                    version.quotaPeriodType(),
                    version.reservationRequired()
                )
            );
        }

        int historyInserted =
            jdbcTemplate.update(
                """
                INSERT INTO subscription_status_history (
                    id,
                    subscription_id,
                    from_status,
                    to_status,
                    changed_by,
                    reason,
                    created_at
                )
                VALUES (
                    ?,
                    ?,
                    NULL,
                    'PENDING',
                    ?,
                    'Administrative subscription creation',
                    ?
                )
                """,
                UUID.randomUUID(),
                subscriptionId,
                actorId,
                now
            );

        if (historyInserted != 1) {
            throw new IllegalStateException(
                "Subscription status history insert failed."
            );
        }

        auditSubscription(
            organizationId,
            actorId,
            subscriptionId,
            command.studentId(),
            version.planId(),
            version.id()
        );

        return new AdminSubscriptionResponse(
            subscriptionId,
            command.studentId(),
            version.planId(),
            version.id(),
            "PENDING",
            command.startsAt(),
            command.endsAt(),
            normalizeNullable(
                command.paymentReference()
            ),
            List.copyOf(
                entitlements
            )
        );
    }

    private UUID organizationId(
        UUID actorId
    ) {
        if (actorId == null) {
            throw new BadCredentialsException(
                "Authenticated user identifier is missing."
            );
        }

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT u.organization_id
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                """,
                (resultSet, rowNumber) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (rows.size() != 1) {
            throw new BadCredentialsException(
                "Authenticated user does not exist or is inactive."
            );
        }

        return rows.getFirst();
    }

    private void requireStudentOwned(
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
            throw notFound(
                "Student does not exist in this organization."
            );
        }
    }

    private void validateAcademicCalendar(
        UUID organizationId,
        UUID calendarId
    ) {
        if (calendarId == null) {
            return;
        }

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM academic_calendars ac
                JOIN campuses c
                  ON c.id = ac.campus_id
                WHERE ac.id = ?
                  AND c.organization_id = ?
                """,
                Long.class,
                calendarId,
                organizationId
            );

        if (
            count == null
            || count != 1
        ) {
            throw notFound(
                "Academic calendar does not exist in this organization."
            );
        }
    }

    private VersionRow version(
        UUID organizationId,
        UUID versionId
    ) {
        OffsetDateTime now =
            OffsetDateTime.now();

        List<VersionRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    spv.id,
                    spv.plan_id,
                    spv.audience_type,
                    spv.quota_value,
                    spv.max_per_day,
                    spv.allowed_days,
                    spv.quota_period_type,
                    spv.reservation_required
                FROM subscription_plan_versions spv
                JOIN subscription_plans sp
                  ON sp.id = spv.plan_id
                WHERE spv.id = ?
                  AND sp.organization_id = ?
                  AND sp.is_active = TRUE
                  AND spv.effective_from <= ?
                  AND (
                        spv.effective_to IS NULL
                        OR spv.effective_to > ?
                  )
                  AND (
                        spv.sale_starts_at IS NULL
                        OR spv.sale_starts_at <= ?
                  )
                  AND (
                        spv.sale_ends_at IS NULL
                        OR spv.sale_ends_at > ?
                  )
                """,
                (resultSet, rowNumber) ->
                    new VersionRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "plan_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "audience_type"
                        ),
                        resultSet.getObject(
                            "quota_value",
                            Integer.class
                        ),
                        resultSet.getObject(
                            "max_per_day",
                            Integer.class
                        ),
                        readSmallints(
                            resultSet.getArray(
                                "allowed_days"
                            )
                        ),
                        resultSet.getString(
                            "quota_period_type"
                        ),
                        resultSet.getBoolean(
                            "reservation_required"
                        )
                    ),
                versionId,
                organizationId,
                now,
                now,
                now,
                now
            );

        if (rows.isEmpty()) {
            throw notFound(
                "Current/effective subscription plan version does not exist."
            );
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Subscription plan version lookup returned multiple rows."
            );
        }

        return rows.getFirst();
    }

    private List<String> versionServices(
        UUID versionId
    ) {
        return jdbcTemplate.query(
            """
            SELECT service_type
            FROM subscription_plan_version_services
            WHERE plan_version_id = ?
            ORDER BY service_type
            """,
            (resultSet, rowNumber) ->
                resultSet.getString(
                    "service_type"
                ),
            versionId
        );
    }

    private List<Integer> normalizeDays(
        List<Integer> values
    ) {
        if (
            values == null
            || values.isEmpty()
        ) {
            throw badRequest(
                "allowedDays is required and must contain at least one day."
            );
        }

        LinkedHashSet<Integer> result =
            new LinkedHashSet<>();

        for (Integer value : values) {
            boolean invalid =
                value == null
                    || value < 1
                    || value > 7;

            if (invalid) {
                throw badRequest(
                    "allowedDays values must be between 1 and 7."
                );
            }

            result.add(
                value
            );
        }

        return List.copyOf(
            result
        );
    }

    private List<String> normalizeServices(
        List<String> values
    ) {
        if (
            values == null
            || values.isEmpty()
        ) {
            throw badRequest(
                "At least one service is required."
            );
        }

        LinkedHashSet<String> result =
            new LinkedHashSet<>();

        for (String value : values) {
            String service =
                upper(
                    value,
                    "services"
                );

            if (!SERVICES.contains(service)) {
                throw badRequest(
                    "Unsupported subscription service: "
                        + service
                );
            }

            result.add(
                service
            );
        }

        if (result.isEmpty()) {
            throw badRequest(
                "At least one service is required."
            );
        }

        return List.copyOf(
            result
        );
    }

    private String smallintArray(
        List<Integer> values
    ) {
        if (values == null) {
            return null;
        }

        StringBuilder builder =
            new StringBuilder(
                "{"
            );

        for (
            int index = 0;
            index < values.size();
            index++
        ) {
            if (index > 0) {
                builder.append(
                    ','
                );
            }

            builder.append(
                values.get(
                    index
                )
            );
        }

        builder.append(
            '}'
        );

        return builder.toString();
    }

    private List<Integer> readSmallints(
        Array sqlArray
    ) {
        if (sqlArray == null) {
            return null;
        }

        try {
            Object raw =
                sqlArray.getArray();

            if (!(raw instanceof Object[] values)) {
                throw new IllegalStateException(
                    "Unexpected PostgreSQL SMALLINT[] representation."
                );
            }

            List<Integer> result =
                new ArrayList<>();

            for (Object value : values) {
                if (value == null) {
                    continue;
                }

                if (!(value instanceof Number number)) {
                    throw new IllegalStateException(
                        "Unexpected allowed day value."
                    );
                }

                result.add(
                    number.intValue()
                );
            }

            return List.copyOf(
                result
            );
        }
        catch (SQLException exception) {
            throw new IllegalStateException(
                "Unable to read allowed day snapshot.",
                exception
            );
        }
    }

    private String serializeRules(
        Map<String, Object> rules
    ) {
        try {
            return jsonMapper.writeValueAsString(
                rules
            );
        }
        catch (JacksonException exception) {
            throw badRequest(
                "rules cannot be serialized."
            );
        }
    }

    private void auditPlan(
        UUID organizationId,
        UUID actorId,
        UUID planId,
        UUID versionId,
        String code
    ) {
        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    id,
                    organization_id,
                    user_id,
                    action,
                    resource_type,
                    resource_id,
                    after_data,
                    source,
                    result
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    'SUBSCRIPTION_PLAN_CREATED',
                    'SUBSCRIPTION_PLAN',
                    ?,
                    jsonb_build_object(
                        'planId',
                        ?::text,
                        'planVersionId',
                        ?::text,
                        'versionNumber',
                        1,
                        'code',
                        ?
                    ),
                    'API',
                    'SUCCESS'
                )
                """,
                UUID.randomUUID(),
                organizationId,
                actorId,
                planId,
                planId,
                versionId,
                code
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Subscription plan audit insert failed."
            );
        }
    }

    private void auditSubscription(
        UUID organizationId,
        UUID actorId,
        UUID subscriptionId,
        UUID studentId,
        UUID planId,
        UUID planVersionId
    ) {
        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    id,
                    organization_id,
                    user_id,
                    action,
                    resource_type,
                    resource_id,
                    after_data,
                    source,
                    result
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    'SUBSCRIPTION_CREATED',
                    'SUBSCRIPTION',
                    ?,
                    jsonb_build_object(
                        'subscriptionId',
                        ?::text,
                        'studentId',
                        ?::text,
                        'planId',
                        ?::text,
                        'planVersionId',
                        ?::text,
                        'status',
                        'PENDING'
                    ),
                    'API',
                    'SUCCESS'
                )
                """,
                UUID.randomUUID(),
                organizationId,
                actorId,
                subscriptionId,
                subscriptionId,
                studentId,
                planId,
                planVersionId
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Subscription audit insert failed."
            );
        }
    }

    private String required(
        String value,
        String field
    ) {
        if (
            value == null
            || value.isBlank()
        ) {
            throw badRequest(
                field
                    + " is required."
            );
        }

        return value.trim();
    }

    private String upper(
        String value,
        String field
    ) {
        return required(
            value,
            field
        ).toUpperCase();
    }

    private String allowed(
        String value,
        String field,
        Set<String> allowed
    ) {
        String normalized =
            upper(
                value,
                field
            );

        if (!allowed.contains(normalized)) {
            throw badRequest(
                "Unsupported "
                    + field
                    + ": "
                    + normalized
            );
        }

        return normalized;
    }

    private String normalizeNullable(
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

    private void validatePositive(
        Integer value,
        String field
    ) {
        if (
            value != null
            && value <= 0
        ) {
            throw badRequest(
                field
                    + " must be positive."
            );
        }
    }

    private void validateNonNegative(
        BigDecimal value,
        String field
    ) {
        if (value == null) {
            throw badRequest(
                field
                    + " is required."
            );
        }

        if (
            value.compareTo(
                BigDecimal.ZERO
            ) < 0
        ) {
            throw badRequest(
                field
                    + " cannot be negative."
            );
        }
    }

    private ResponseStatusException badRequest(
        String message
    ) {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            message
        );
    }

    private ResponseStatusException notFound(
        String message
    ) {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            message
        );
    }

    private ResponseStatusException conflict(
        String message
    ) {
        return new ResponseStatusException(
            HttpStatus.CONFLICT,
            message
        );
    }

    private record VersionRow(
        UUID id,
        UUID planId,
        String audienceType,
        Integer quotaValue,
        Integer maxPerDay,
        List<Integer> allowedDays,
        String quotaPeriodType,
        boolean reservationRequired
    ) {
    }
}