package com.sup2i.food.slot.service;

import com.sup2i.food.slot.api.dto.TimeSlotResponse;
import com.sup2i.food.slot.domain.TimeSlotStatus;
import com.sup2i.food.slot.exception.TimeSlotErrorCode;
import com.sup2i.food.slot.exception.TimeSlotException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class TimeSlotService {

    private static final DateTimeFormatter
        TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                "HH:mm"
            );

    private final JdbcTemplate jdbcTemplate;

    private final PreorderPolicyService
        preorderPolicyService;

    public TimeSlotService(
        JdbcTemplate jdbcTemplate,
        PreorderPolicyService preorderPolicyService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.preorderPolicyService =
            preorderPolicyService;
    }

    @Transactional(readOnly = true)
    public List<TimeSlotResponse> list(
        UUID actorId,
        UUID locationId,
        LocalDate date
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        if (locationId == null) {

            throw new IllegalArgumentException(
                "locationId is required."
            );
        }

        if (date == null) {

            throw new IllegalArgumentException(
                "date is required."
            );
        }

        List<TimeSlotRow> slots =
            jdbcTemplate.query(
                """
                SELECT
                    ts.id,
                    ts.location_id,
                    c.id AS campus_id,
                    c.timezone AS campus_timezone,
                    ts.slot_date,
                    ts.start_time,
                    ts.end_time,
                    ts.capacity,
                    ts.reserved_count,
                    ts.status
                FROM time_slots ts
                JOIN locations l
                  ON l.id = ts.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE ts.location_id = ?
                  AND ts.slot_date = ?
                  AND c.organization_id = ?
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                ORDER BY
                    ts.start_time ASC,
                    ts.id ASC
                """,
                this::mapRow,
                locationId,
                date,
                organizationId
            );

        return slots
            .stream()
            .map(
                this::effectiveResponse
            )
            .toList();
    }

    @Transactional(readOnly = true)
    public TimeSlotResponse validateSelection(
        UUID actorId,
        UUID slotId,
        UUID expectedLocationId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        TimeSlotRow slot =
            selectableSlot(
                organizationId,
                slotId,
                expectedLocationId
            );

        requireOperationalWindow(
            slot
        );

        requireReservable(
            slot
        );

        return response(
            slot,
            slot.reservedCount(),
            slot.status()
        );
    }

    @Transactional(readOnly = true)
    public TimeSlotResponse findForOrder(
        UUID organizationId,
        UUID slotId,
        UUID expectedLocationId
    ) {

        TimeSlotRow slot =
            existingSlot(
                organizationId,
                slotId,
                expectedLocationId
            );

        return response(
            slot,
            slot.reservedCount(),
            slot.status()
        );
    }

    @Transactional
    public TimeSlotResponse reserve(
        UUID actorId,
        UUID orderId,
        UUID slotId,
        UUID expectedLocationId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        TimeSlotRow slot =
            lockReservableSlot(
                organizationId,
                slotId,
                expectedLocationId
            );

        requireOperationalWindow(
            slot
        );

        requireReservable(
            slot
        );

        int newReservedCount =
            slot.reservedCount()
                + 1;

        TimeSlotStatus nextStatus =
            slot.status();

        if (
            newReservedCount
                == slot.capacity()
        ) {

            nextStatus =
                TimeSlotStatus.FULL;
        }

        updateCapacity(
            slot.id(),
            newReservedCount,
            nextStatus
        );

        persistReservation(
            orderId,
            slot.id()
        );

        return response(
            slot,
            newReservedCount,
            nextStatus
        );
    }

    @Transactional
    public TimeSlotResponse releaseCancelled(
        UUID actorId,
        UUID orderId,
        UUID slotId,
        UUID expectedLocationId
    ) {

        return release(
            actorId,
            orderId,
            slotId,
            expectedLocationId,
            "CANCELLED",
            "ORDER_CANCELLED"
        );
    }

    @Transactional
    public TimeSlotResponse releaseExpired(
        UUID actorId,
        UUID orderId,
        UUID slotId,
        UUID expectedLocationId
    ) {

        return release(
            actorId,
            orderId,
            slotId,
            expectedLocationId,
            "EXPIRED",
            "ORDER_EXPIRED"
        );
    }

    @Transactional
    public TimeSlotResponse releaseExpiredSystem(
        UUID organizationId,
        UUID orderId,
        UUID slotId,
        UUID expectedLocationId
    ) {

        if (organizationId == null) {
            throw new IllegalArgumentException(
                "organizationId is required."
            );
        }

        TimeSlotRow slot =
            lockExistingSlot(
                organizationId,
                slotId,
                expectedLocationId
            );

        UUID reservationId =
            lockActiveReservation(
                organizationId,
                orderId,
                slot.id()
            );

        if (
            slot.reservedCount()
                == 0
        ) {
            throw new IllegalStateException(
                "Active slot reservation exists with reserved_count = 0."
            );
        }

        int newReservedCount =
            slot.reservedCount()
                - 1;

        TimeSlotStatus nextStatus =
            slot.status();

        if (newReservedCount == 0) {

            if (
                slot.status()
                    != TimeSlotStatus.CLOSED
            ) {
                nextStatus =
                    TimeSlotStatus.OPEN;
            }
        }
        else {

            if (
                slot.status()
                    == TimeSlotStatus.FULL
            ) {
                nextStatus =
                    TimeSlotStatus.ALMOST_FULL;
            }
        }

        updateCapacity(
            slot.id(),
            newReservedCount,
            nextStatus
        );

        closeReservation(
            reservationId,
            "EXPIRED",
            "ORDER_EXPIRED"
        );

        return response(
            slot,
            newReservedCount,
            nextStatus
        );
    }

    private TimeSlotResponse release(
        UUID actorId,
        UUID orderId,
        UUID slotId,
        UUID expectedLocationId,
        String finalStatus,
        String releaseReason
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        TimeSlotRow slot =
            lockExistingSlot(
                organizationId,
                slotId,
                expectedLocationId
            );

        UUID reservationId =
            lockActiveReservation(
                organizationId,
                orderId,
                slot.id()
            );

        if (
            slot.reservedCount()
                == 0
        ) {

            throw new IllegalStateException(
                "Active slot reservation exists with reserved_count = 0."
            );
        }

        int newReservedCount =
            slot.reservedCount()
                - 1;

        TimeSlotStatus nextStatus =
            slot.status();

        if (newReservedCount == 0) {

            if (
                slot.status()
                    != TimeSlotStatus.CLOSED
            ) {

                nextStatus =
                    TimeSlotStatus.OPEN;
            }
        }
        else {

            if (
                slot.status()
                    == TimeSlotStatus.FULL
            ) {

                nextStatus =
                    TimeSlotStatus.ALMOST_FULL;
            }
        }

        updateCapacity(
            slot.id(),
            newReservedCount,
            nextStatus
        );

        closeReservation(
            reservationId,
            finalStatus,
            releaseReason
        );

        return response(
            slot,
            newReservedCount,
            nextStatus
        );
    }

    @Transactional(readOnly = true)
    public OffsetDateTime paymentDeadline(
        UUID actorId,
        UUID slotId,
        UUID expectedLocationId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        TimeSlotRow slot =
            selectableSlot(
                organizationId,
                slotId,
                expectedLocationId
            );

        return preorderPolicyService
            .paymentDeadline(
                slot.locationId(),
                slot.campusId(),
                slot.campusTimezone(),
                slot.date(),
                slot.startTime(),
                slot.endTime()
            );
    }

    private void persistReservation(
        UUID orderId,
        UUID slotId
    ) {

        if (orderId == null) {

            throw new IllegalArgumentException(
                "orderId is required for slot reservation."
            );
        }

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO time_slot_reservations (
                    id,
                    time_slot_id,
                    order_id,
                    status,
                    reserved_at
                )
                VALUES (
                    ?, ?, ?, 'ACTIVE',
                    CURRENT_TIMESTAMP
                )
                """,
                UUID.randomUUID(),
                slotId,
                orderId
            );

        if (inserted != 1) {

            throw new IllegalStateException(
                "Slot reservation insert did not affect exactly one row."
            );
        }
    }

    private UUID lockActiveReservation(
        UUID organizationId,
        UUID orderId,
        UUID slotId
    ) {

        if (orderId == null) {

            throw new IllegalArgumentException(
                "orderId is required for slot release."
            );
        }

        List<UUID> reservations =
            jdbcTemplate.query(
                """
                SELECT tsr.id
                FROM time_slot_reservations tsr
                JOIN orders o
                  ON o.id = tsr.order_id
                WHERE tsr.order_id = ?
                  AND tsr.time_slot_id = ?
                  AND tsr.status = 'ACTIVE'
                  AND o.organization_id = ?
                FOR UPDATE OF tsr
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                orderId,
                slotId,
                organizationId
            );

        if (reservations.isEmpty()) {

            throw new IllegalStateException(
                "Active slot reservation does not exist."
            );
        }

        if (reservations.size() != 1) {

            throw new IllegalStateException(
                "Multiple active slot reservations exist for the order."
            );
        }

        return reservations.get(0);
    }

    private void closeReservation(
        UUID reservationId,
        String finalStatus,
        String releaseReason
    ) {

        boolean validStatus =
            "CANCELLED".equals(
                finalStatus
            )
            || "EXPIRED".equals(
                finalStatus
            );

        if (!validStatus) {

            throw new IllegalArgumentException(
                "Unsupported slot reservation release status."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE time_slot_reservations
                SET status = ?,
                    released_at = CURRENT_TIMESTAMP,
                    release_reason = ?
                WHERE id = ?
                  AND status = 'ACTIVE'
                """,
                finalStatus,
                releaseReason,
                reservationId
            );

        if (updated != 1) {

            throw new IllegalStateException(
                "Slot reservation release did not affect exactly one row."
            );
        }
    }

    private void requireOperationalWindow(
        TimeSlotRow slot
    ) {

        preorderPolicyService
            .requireOperationalWindow(
                slot.locationId(),
                slot.campusId(),
                slot.campusTimezone(),
                slot.date(),
                slot.startTime(),
                slot.endTime()
            );
    }

    private TimeSlotResponse effectiveResponse(
        TimeSlotRow slot
    ) {

        if (
            slot.status()
                == TimeSlotStatus.CLOSED
        ) {

            return response(
                slot,
                slot.reservedCount(),
                TimeSlotStatus.CLOSED
            );
        }

        boolean operational =
            preorderPolicyService
                .isOperationallyOpen(
                    slot.locationId(),
                    slot.campusId(),
                    slot.campusTimezone(),
                    slot.date(),
                    slot.startTime(),
                    slot.endTime()
                );

        TimeSlotStatus effectiveStatus;

        if (operational) {

            effectiveStatus =
                slot.status();
        }
        else {

            effectiveStatus =
                TimeSlotStatus.CLOSED;
        }

        return response(
            slot,
            slot.reservedCount(),
            effectiveStatus
        );
    }

    private void requireReservable(
        TimeSlotRow slot
    ) {

        if (
            slot.status()
                == TimeSlotStatus.CLOSED
        ) {

            throw new TimeSlotException(
                TimeSlotErrorCode.SLOT_CLOSED,
                "Time slot is closed."
            );
        }

        if (
            slot.status()
                == TimeSlotStatus.FULL
        ) {

            throw new TimeSlotException(
                TimeSlotErrorCode.SLOT_FULL,
                "Time slot has no remaining capacity."
            );
        }

        if (
            slot.reservedCount()
                >= slot.capacity()
        ) {

            throw new TimeSlotException(
                TimeSlotErrorCode.SLOT_FULL,
                "Time slot has no remaining capacity."
            );
        }
    }

    private UUID organizationId(
        UUID actorId
    ) {

        if (actorId == null) {

            throw new BadCredentialsException(
                "Authenticated user is missing."
            );
        }

        List<UUID> organizations =
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

        if (organizations.isEmpty()) {

            throw new BadCredentialsException(
                "Authenticated user does not exist or is not active."
            );
        }

        return organizations.get(0);
    }

    private TimeSlotRow selectableSlot(
        UUID organizationId,
        UUID slotId,
        UUID expectedLocationId
    ) {

        return querySlot(
            organizationId,
            slotId,
            expectedLocationId,
            true,
            false
        );
    }

    private TimeSlotRow existingSlot(
        UUID organizationId,
        UUID slotId,
        UUID expectedLocationId
    ) {

        return querySlot(
            organizationId,
            slotId,
            expectedLocationId,
            false,
            false
        );
    }

    private TimeSlotRow lockReservableSlot(
        UUID organizationId,
        UUID slotId,
        UUID expectedLocationId
    ) {

        return querySlot(
            organizationId,
            slotId,
            expectedLocationId,
            true,
            true
        );
    }

    private TimeSlotRow lockExistingSlot(
        UUID organizationId,
        UUID slotId,
        UUID expectedLocationId
    ) {

        return querySlot(
            organizationId,
            slotId,
            expectedLocationId,
            false,
            true
        );
    }

    private TimeSlotRow querySlot(
        UUID organizationId,
        UUID slotId,
        UUID expectedLocationId,
        boolean activeOnly,
        boolean lock
    ) {

        requireIdentity(
            slotId,
            expectedLocationId
        );

        StringBuilder sql =
            new StringBuilder(
                """
                SELECT
                    ts.id,
                    ts.location_id,
                    c.id AS campus_id,
                    c.timezone AS campus_timezone,
                    ts.slot_date,
                    ts.start_time,
                    ts.end_time,
                    ts.capacity,
                    ts.reserved_count,
                    ts.status
                FROM time_slots ts
                JOIN locations l
                  ON l.id = ts.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE ts.id = ?
                  AND ts.location_id = ?
                  AND c.organization_id = ?
                """
            );

        if (activeOnly) {

            sql.append(
                """
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                """
            );
        }

        if (lock) {

            sql.append(
                """
                FOR UPDATE OF ts
                """
            );
        }

        String query =
            sql.toString();

        List<TimeSlotRow> slots =
            jdbcTemplate.query(
                query,
                this::mapRow,
                slotId,
                expectedLocationId,
                organizationId
            );

        return requireSingle(
            slots
        );
    }

    private void requireIdentity(
        UUID slotId,
        UUID expectedLocationId
    ) {

        boolean missing =
            slotId == null
                || expectedLocationId == null;

        if (missing) {

            throw new TimeSlotException(
                TimeSlotErrorCode.SLOT_NOT_FOUND,
                "Time slot does not exist."
            );
        }
    }

    private TimeSlotRow requireSingle(
        List<TimeSlotRow> slots
    ) {

        if (slots.isEmpty()) {

            throw new TimeSlotException(
                TimeSlotErrorCode.SLOT_NOT_FOUND,
                "Time slot does not exist."
            );
        }

        if (slots.size() != 1) {

            throw new IllegalStateException(
                "Time slot lookup returned multiple rows."
            );
        }

        return slots.get(0);
    }

    private void updateCapacity(
        UUID slotId,
        int reservedCount,
        TimeSlotStatus status
    ) {

        int updated =
            jdbcTemplate.update(
                """
                UPDATE time_slots
                SET reserved_count = ?,
                    status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                reservedCount,
                status.name(),
                slotId
            );

        if (updated != 1) {

            throw new IllegalStateException(
                "Time slot capacity update did not affect exactly one row."
            );
        }
    }

    private TimeSlotRow mapRow(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {

        return new TimeSlotRow(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "location_id",
                UUID.class
            ),
            resultSet.getObject(
                "campus_id",
                UUID.class
            ),
            resultSet.getString(
                "campus_timezone"
            ),
            resultSet.getObject(
                "slot_date",
                LocalDate.class
            ),
            localTime(
                resultSet,
                "start_time"
            ),
            localTime(
                resultSet,
                "end_time"
            ),
            resultSet.getInt(
                "capacity"
            ),
            resultSet.getInt(
                "reserved_count"
            ),
            TimeSlotStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            )
        );
    }

    private LocalTime localTime(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        LocalTime value =
            resultSet.getObject(
                column,
                LocalTime.class
            );

        if (value == null) {

            throw new IllegalStateException(
                "Time slot contains a null time."
            );
        }

        return value;
    }

    private TimeSlotResponse response(
        TimeSlotRow slot,
        int reservedCount,
        TimeSlotStatus status
    ) {

        int remainingCapacity =
            slot.capacity()
                - reservedCount;

        if (remainingCapacity < 0) {

            throw new IllegalStateException(
                "Time slot remaining capacity became negative."
            );
        }

        String start =
            slot.startTime()
                .format(
                    TIME_FORMAT
                );

        String end =
            slot.endTime()
                .format(
                    TIME_FORMAT
                );

        return new TimeSlotResponse(
            slot.id(),
            slot.date(),
            start,
            end,
            slot.capacity(),
            reservedCount,
            remainingCapacity,
            status
        );
    }

    private TimeSlotStatus normalizedZeroStatus(
        TimeSlotStatus status
    ) {

        if (
            status
                == TimeSlotStatus.CLOSED
        ) {

            return status;
        }

        return TimeSlotStatus.OPEN;
    }

    private record TimeSlotRow(
        UUID id,
        UUID locationId,
        UUID campusId,
        String campusTimezone,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int capacity,
        int reservedCount,
        TimeSlotStatus status
    ) {
    }
}