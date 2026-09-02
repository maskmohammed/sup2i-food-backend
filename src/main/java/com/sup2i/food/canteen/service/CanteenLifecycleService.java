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
public class CanteenLifecycleService {

    private final JdbcTemplate jdbcTemplate;

    public CanteenLifecycleService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public boolean markReservationNoShowSystem(
        UUID reservationId
    ) {

        if (reservationId == null) {
            throw new IllegalArgumentException(
                "reservationId is required."
            );
        }

        List<ReservationRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    reservation.id,
                    reservation.status,
                    menu.menu_date,
                    menu.status AS menu_status,
                    campus.organization_id,
                    (
                        CURRENT_TIMESTAMP
                        AT TIME ZONE campus.timezone
                    )::date AS local_date
                FROM canteen_reservations reservation
                JOIN canteen_menus menu
                  ON menu.id = reservation.menu_id
                JOIN locations location
                  ON location.id = menu.location_id
                JOIN campuses campus
                  ON campus.id = location.campus_id
                WHERE reservation.id = ?
                  AND reservation.student_id IS NOT NULL
                FOR UPDATE OF reservation
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new ReservationRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "menu_date",
                            LocalDate.class
                        ),
                        resultSet.getString(
                            "menu_status"
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
                reservationId
            );

        if (rows.isEmpty()) {
            return false;
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Canteen reservation lookup returned multiple rows."
            );
        }

        ReservationRow reservation =
            rows.get(0);

        if (
            !"RESERVED".equals(
                reservation.status()
            )
        ) {
            return false;
        }

        boolean serviceWasValid =
            "PUBLISHED".equals(
                reservation.menuStatus()
            )
                || "CLOSED".equals(
                    reservation.menuStatus()
                );

        if (!serviceWasValid) {
            return false;
        }

        if (
            reservation.menuDate() == null
            || reservation.localDate() == null
        ) {
            throw new IllegalStateException(
                "Reservation service date is unavailable."
            );
        }

        /*
         * No normative intra-day end timestamp exists for the
         * canteen menu service.
         *
         * Fail-safe behavior:
         * mark NO_SHOW only after the complete menu calendar
         * day has elapsed in the campus timezone.
         */
        if (
            !reservation.menuDate()
                .isBefore(
                    reservation.localDate()
                )
        ) {
            return false;
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE canteen_reservations
                SET status = 'NO_SHOW'
                WHERE id = ?
                  AND status = 'RESERVED'
                """,
                reservation.id()
            );

        if (updated != 1) {
            throw new IllegalStateException(
                "Canteen reservation no-show update failed."
            );
        }

        int auditInserted =
            jdbcTemplate.update(
                """
                INSERT INTO audit_logs(
                    organization_id,
                    user_id,
                    action,
                    resource_type,
                    resource_id,
                    before_data,
                    after_data,
                    reason,
                    source,
                    result
                )
                VALUES(
                    ?,
                    NULL,
                    'CANTEEN_RESERVATION_NO_SHOW',
                    'CANTEEN_RESERVATION',
                    ?,
                    jsonb_build_object(
                        'status',
                        'RESERVED'
                    ),
                    jsonb_build_object(
                        'status',
                        'NO_SHOW'
                    ),
                    'Meal service day elapsed without consumption.',
                    'BACKEND',
                    'SUCCESS'
                )
                """,
                reservation.organizationId(),
                reservation.id()
            );

        if (auditInserted != 1) {
            throw new IllegalStateException(
                "Canteen no-show audit insert failed."
            );
        }

        return true;
    }

    @Transactional
    public boolean reverseUsage(
        UUID actorId,
        UUID usageId,
        String reason
    ) {

        if (actorId == null) {
            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Actor id is required."
            );
        }

        if (usageId == null) {
            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Meal usage id is required."
            );
        }

        String normalizedReason =
            normalizeReason(
                reason
            );

        UUID organizationId =
            requireActorOrganization(
                actorId
            );

        List<UsageRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    usage.id,
                    usage.status,
                    plan.organization_id
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
                WHERE usage.id = ?
                  AND plan.organization_id = ?
                  AND usage.student_id IS NOT NULL
                FOR UPDATE OF usage
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new UsageRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "organization_id",
                            UUID.class
                        )
                    ),
                usageId,
                organizationId
            );

        if (rows.isEmpty()) {
            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Meal usage does not exist."
            );
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Meal usage lookup returned multiple rows."
            );
        }

        UsageRow usage =
            rows.get(0);

        if (
            "REVERSED".equals(
                usage.status()
            )
        ) {
            return false;
        }

        if (
            !"VALID".equals(
                usage.status()
            )
        ) {
            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Only a VALID meal usage can be reversed."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE meal_usages
                SET
                    status = 'REVERSED',
                    reversed_at = CURRENT_TIMESTAMP,
                    reversed_by = ?,
                    reversal_reason = ?
                WHERE id = ?
                  AND status = 'VALID'
                """,
                actorId,
                normalizedReason,
                usage.id()
            );

        if (updated != 1) {
            throw new IllegalStateException(
                "Meal usage reversal update failed."
            );
        }

        int auditInserted =
            jdbcTemplate.update(
                """
                INSERT INTO audit_logs(
                    organization_id,
                    user_id,
                    action,
                    resource_type,
                    resource_id,
                    before_data,
                    after_data,
                    reason,
                    source,
                    result
                )
                VALUES(
                    ?,
                    ?,
                    'MEAL_USAGE_REVERSED',
                    'MEAL_USAGE',
                    ?,
                    jsonb_build_object(
                        'status',
                        'VALID'
                    ),
                    jsonb_build_object(
                        'status',
                        'REVERSED'
                    ),
                    ?,
                    'BACKEND',
                    'SUCCESS'
                )
                """,
                usage.organizationId(),
                actorId,
                usage.id(),
                normalizedReason
            );

        if (auditInserted != 1) {
            throw new IllegalStateException(
                "Meal usage reversal audit insert failed."
            );
        }

        /*
         * No physical deletion.
         *
         * Quota calculations already ignore REVERSED usages.
         *
         * A linked reservation remains CONSUMED because the
         * frozen reservation lifecycle defines no backwards
         * CONSUMED -> RESERVED transition.
         */
        return true;
    }

    private UUID requireActorOrganization(
        UUID actorId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT user_account.organization_id
                FROM users user_account
                JOIN organizations organization
                  ON organization.id =
                     user_account.organization_id
                WHERE user_account.id = ?
                  AND user_account.status = 'ACTIVE'
                  AND organization.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (rows.isEmpty()) {
            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Active actor does not exist."
            );
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Actor organization lookup returned multiple rows."
            );
        }

        UUID organizationId =
            rows.get(0);

        if (organizationId == null) {
            throw new IllegalStateException(
                "Actor organization is missing."
            );
        }

        return organizationId;
    }

    private String normalizeReason(
        String reason
    ) {

        if (reason == null) {
            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Reversal reason is required."
            );
        }

        String normalized =
            reason.trim();

        if (normalized.isEmpty()) {
            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Reversal reason is required."
            );
        }

        return normalized;
    }

    private record ReservationRow(
        UUID id,
        String status,
        LocalDate menuDate,
        String menuStatus,
        UUID organizationId,
        LocalDate localDate
    ) {
    }

    private record UsageRow(
        UUID id,
        String status,
        UUID organizationId
    ) {
    }
}