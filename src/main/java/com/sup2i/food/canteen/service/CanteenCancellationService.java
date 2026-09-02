package com.sup2i.food.canteen.service;

import com.sup2i.food.canteen.api.dto.CanteenReservationResponse;
import com.sup2i.food.canteen.exception.CanteenErrorCode;
import com.sup2i.food.canteen.exception.CanteenException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CanteenCancellationService {

    private final JdbcTemplate jdbcTemplate;

    public CanteenCancellationService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public CanteenReservationResponse cancel(
        UUID actorId,
        UUID reservationId
    ) {
        if (reservationId == null) {
            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "reservationId is required."
            );
        }

        UUID organizationId =
            organizationId(
                actorId
            );

        ReservationRow reservation =
            lockReservation(
                organizationId,
                reservationId
            );

        if ("CANCELLED".equals(reservation.status())) {
            return response(
                reservation,
                "CANCELLED"
            );
        }

        if (!"RESERVED".equals(reservation.status())) {
            throw new CanteenException(
                CanteenErrorCode.CANTEEN_RESERVATION_CLOSED,
                "Only a RESERVED canteen reservation can be cancelled."
            );
        }

        if (reservation.studentId() == null) {
            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Non-student reservation cancellation is outside the MVP endpoint."
            );
        }

        LocalTime deadline =
            cancellationDeadline(
                organizationId,
                reservation
            );

        enforceDeadline(
            reservation,
            deadline
        );

        OffsetDateTime cancelledAt =
            OffsetDateTime.now();

        int updated =
            jdbcTemplate.update(
                """
                UPDATE canteen_reservations
                SET status = 'CANCELLED',
                    cancelled_at = ?
                WHERE id = ?
                  AND status = 'RESERVED'
                """,
                cancelledAt,
                reservation.id()
            );

        if (updated != 1) {
            throw new IllegalStateException(
                "Canteen reservation cancellation transition failed."
            );
        }

        audit(
            organizationId,
            actorId,
            reservation,
            cancelledAt
        );

        return response(
            reservation,
            "CANCELLED"
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

    private ReservationRow lockReservation(
        UUID organizationId,
        UUID reservationId
    ) {
        List<ReservationRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    cr.id,
                    cr.menu_id,
                    cr.student_id,
                    cr.status,
                    cr.reserved_at,
                    cm.menu_date,
                    cm.meal_type,
                    c.timezone
                FROM canteen_reservations cr
                JOIN canteen_menus cm
                  ON cm.id = cr.menu_id
                JOIN locations l
                  ON l.id = cm.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE cr.id = ?
                  AND c.organization_id = ?
                FOR UPDATE OF cr
                """,
                (resultSet, rowNumber) ->
                    new ReservationRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "menu_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "student_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "reserved_at",
                            OffsetDateTime.class
                        ),
                        resultSet.getObject(
                            "menu_date",
                            LocalDate.class
                        ),
                        resultSet.getString(
                            "meal_type"
                        ),
                        resultSet.getString(
                            "timezone"
                        )
                    ),
                reservationId,
                organizationId
            );

        if (rows.isEmpty()) {
            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Canteen reservation does not exist."
            );
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Canteen reservation lookup returned multiple rows."
            );
        }

        return rows.getFirst();
    }

    private LocalTime cancellationDeadline(
        UUID organizationId,
        ReservationRow reservation
    ) {
        short day =
            (short) reservation
                .menuDate()
                .getDayOfWeek()
                .getValue();

        List<LocalTime> rows =
            jdbcTemplate.query(
                """
                SELECT
                    MIN(
                        spv.reservation_cancellation_deadline
                    ) AS cancellation_deadline
                FROM subscriptions s
                JOIN subscription_plans sp
                  ON sp.id = s.plan_id
                JOIN subscription_plan_versions spv
                  ON spv.id = s.plan_version_id
                 AND spv.plan_id = s.plan_id
                JOIN meal_entitlements me
                  ON me.subscription_id = s.id
                WHERE s.student_id = ?
                  AND s.meal_beneficiary_id IS NULL
                  AND s.status IN (
                        'PENDING',
                        'ACTIVE',
                        'SUSPENDED'
                  )
                  AND ? BETWEEN s.starts_at
                            AND s.ends_at
                  AND sp.organization_id = ?
                  AND spv.audience_type = 'STUDENT'
                  AND me.meal_type = ?
                  AND ? BETWEEN me.valid_from
                            AND me.valid_to
                  AND (
                        me.allowed_days IS NULL
                        OR ?::SMALLINT = ANY(me.allowed_days)
                  )
                  AND (
                        spv.allowed_days IS NULL
                        OR ?::SMALLINT = ANY(spv.allowed_days)
                  )
                  AND EXISTS (
                        SELECT 1
                        FROM subscription_plan_version_services spvs
                        WHERE spvs.plan_version_id = spv.id
                          AND spvs.service_type = ?
                  )
                """,
                (resultSet, rowNumber) ->
                    resultSet.getObject(
                        "cancellation_deadline",
                        LocalTime.class
                    ),
                reservation.studentId(),
                reservation.menuDate(),
                organizationId,
                reservation.mealType(),
                reservation.menuDate(),
                day,
                day,
                reservation.mealType()
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.getFirst();
    }

    private void enforceDeadline(
        ReservationRow reservation,
        LocalTime deadline
    ) {
        if (deadline == null) {
            return;
        }

        ZoneId zoneId;

        try {
            zoneId =
                ZoneId.of(
                    reservation.timezone()
                );
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException(
                "Campus timezone is invalid.",
                exception
            );
        }

        ZonedDateTime cutoff =
            reservation
                .menuDate()
                .atTime(
                    deadline
                )
                .atZone(
                    zoneId
                );

        ZonedDateTime now =
            ZonedDateTime.now(
                zoneId
            );

        if (!now.isBefore(cutoff)) {
            throw new CanteenException(
                CanteenErrorCode.CANTEEN_RESERVATION_CLOSED,
                "Canteen reservation cancellation deadline has passed."
            );
        }
    }

    private void audit(
        UUID organizationId,
        UUID actorId,
        ReservationRow reservation,
        OffsetDateTime cancelledAt
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
                    before_data,
                    after_data,
                    source,
                    result
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    'CANTEEN_RESERVATION_CANCELLED',
                    'CANTEEN_RESERVATION',
                    ?,
                    jsonb_build_object(
                        'status',
                        'RESERVED',
                        'menuId',
                        ?::text
                    ),
                    jsonb_build_object(
                        'status',
                        'CANCELLED',
                        'menuId',
                        ?::text,
                        'cancelledAt',
                        ?::text
                    ),
                    'API',
                    'SUCCESS'
                )
                """,
                UUID.randomUUID(),
                organizationId,
                actorId,
                reservation.id(),
                reservation.menuId(),
                reservation.menuId(),
                cancelledAt
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Canteen cancellation audit insert failed."
            );
        }
    }

    private CanteenReservationResponse response(
        ReservationRow reservation,
        String status
    ) {
        return new CanteenReservationResponse(
            reservation.id(),
            reservation.menuId(),
            reservation.studentId(),
            status,
            reservation.reservedAt()
        );
    }

    private record ReservationRow(
        UUID id,
        UUID menuId,
        UUID studentId,
        String status,
        OffsetDateTime reservedAt,
        LocalDate menuDate,
        String mealType,
        String timezone
    ) {
    }
}