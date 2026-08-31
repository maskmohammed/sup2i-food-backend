package com.sup2i.food.event.service;

import com.sup2i.food.event.api.dto.CampusEventResponse;
import com.sup2i.food.event.api.dto.CreateCampusEventCommand;
import com.sup2i.food.eventing.exception.GroupEventConflictException;
import com.sup2i.food.eventing.exception.GroupEventNotFoundException;
import com.sup2i.food.eventing.exception.GroupEventValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class CampusEventService {

    private final JdbcTemplate jdbcTemplate;

    public CampusEventService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CampusEventResponse create(
        UUID actorId,
        UUID eventId,
        CreateCampusEventCommand command
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            eventId,
            "Campus event id"
        );

        validate(command);

        String name =
            requiredText(
                command.name(),
                "Campus event name",
                180
            );

        String eventType =
            requiredText(
                command.eventType(),
                "Campus event type",
                40
            );

        String description =
            nullableText(
                command.description()
            );

        CampusEventResponse existing =
            find(
                organizationId,
                eventId
            );

        if (existing != null) {

            boolean same =
                Objects.equals(existing.campusId(), command.campusId())
                    && Objects.equals(existing.name(), name)
                    && Objects.equals(existing.eventType(), eventType)
                    && samePostgresTimestamp(existing.startsAt(), command.startsAt())
                    && samePostgresTimestamp(existing.endsAt(), command.endsAt())
                    && Objects.equals(
                        existing.expectedAttendance(),
                        command.expectedAttendance()
                    )
                    && Objects.equals(
                        existing.description(),
                        description
                    )
                    && Objects.equals(
                        existing.createdBy(),
                        actorId
                    );

            if (same) {
                return replay(existing);
            }

            throw new GroupEventConflictException(
                "Campus event identifier is already used by another payload."
            );
        }

        requireOwnedActiveCampus(
            organizationId,
            command.campusId()
        );

        try {

            jdbcTemplate.update(
                """
                INSERT INTO campus_events(
                    id,
                    campus_id,
                    name,
                    event_type,
                    starts_at,
                    ends_at,
                    expected_attendance,
                    description,
                    created_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventId,
                command.campusId(),
                name,
                eventType,
                command.startsAt(),
                command.endsAt(),
                command.expectedAttendance(),
                description,
                actorId
            );

        } catch (DataIntegrityViolationException exception) {

            throw new GroupEventConflictException(
                "Campus event violates schema or tenant invariants."
            );
        }

        return get(
            actorId,
            eventId
        );
    }

    @Transactional(readOnly = true)
    public CampusEventResponse get(
        UUID actorId,
        UUID eventId
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            eventId,
            "Campus event id"
        );

        CampusEventResponse event =
            find(
                organizationId,
                eventId
            );

        if (event == null) {
            throw new GroupEventNotFoundException(
                "Campus event does not exist."
            );
        }

        return event;
    }

    @Transactional(readOnly = true)
    public List<CampusEventResponse> list(
        UUID actorId,
        UUID campusId
    ) {

        UUID organizationId =
            organizationId(actorId);

        if (campusId == null) {

            return jdbcTemplate.query(
                """
                SELECT
                    ce.id,
                    ce.campus_id,
                    ce.name,
                    ce.event_type,
                    ce.starts_at,
                    ce.ends_at,
                    ce.expected_attendance,
                    ce.description,
                    ce.created_by,
                    ce.created_at,
                    ce.updated_at
                FROM campus_events ce
                JOIN campuses c
                  ON c.id = ce.campus_id
                WHERE c.organization_id = ?
                ORDER BY ce.starts_at ASC, ce.id ASC
                """,
                (resultSet, rowNumber) ->
                    response(
                        resultSet,
                        false
                    ),
                organizationId
            );
        }

        requireOwnedActiveCampus(
            organizationId,
            campusId
        );

        return jdbcTemplate.query(
            """
            SELECT
                ce.id,
                ce.campus_id,
                ce.name,
                ce.event_type,
                ce.starts_at,
                ce.ends_at,
                ce.expected_attendance,
                ce.description,
                ce.created_by,
                ce.created_at,
                ce.updated_at
            FROM campus_events ce
            JOIN campuses c
              ON c.id = ce.campus_id
            WHERE ce.campus_id = ?
              AND c.organization_id = ?
            ORDER BY ce.starts_at ASC, ce.id ASC
            """,
            (resultSet, rowNumber) ->
                response(
                    resultSet,
                    false
                ),
            campusId,
            organizationId
        );
    }

    private CampusEventResponse find(
        UUID organizationId,
        UUID eventId
    ) {

        List<CampusEventResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    ce.id,
                    ce.campus_id,
                    ce.name,
                    ce.event_type,
                    ce.starts_at,
                    ce.ends_at,
                    ce.expected_attendance,
                    ce.description,
                    ce.created_by,
                    ce.created_at,
                    ce.updated_at
                FROM campus_events ce
                JOIN campuses c
                  ON c.id = ce.campus_id
                WHERE ce.id = ?
                  AND c.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    response(
                        resultSet,
                        false
                    ),
                eventId,
                organizationId
            );

        if (rows.size() > 1) {
            throw new GroupEventConflictException(
                "Multiple campus events matched one identifier."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private void requireOwnedActiveCampus(
        UUID organizationId,
        UUID campusId
    ) {

        requireId(
            campusId,
            "Campus id"
        );

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM campuses
                WHERE id = ?
                  AND organization_id = ?
                  AND is_active = TRUE
                """,
                Integer.class,
                campusId,
                organizationId
            );

        if (
            count == null
                || count != 1
        ) {
            throw new GroupEventNotFoundException(
                "Campus does not exist in actor organization."
            );
        }
    }

    private UUID organizationId(
        UUID actorId
    ) {

        requireId(
            actorId,
            "Actor id"
        );

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT organization_id
                FROM users
                WHERE id = ?
                  AND status = 'ACTIVE'
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
                "Authenticated user does not exist."
            );
        }

        return rows.get(0);
    }

    private void validate(
        CreateCampusEventCommand command
    ) {

        if (command == null) {
            throw new GroupEventValidationException(
                "Campus event payload is required."
            );
        }

        requireId(
            command.campusId(),
            "Campus id"
        );

        requiredText(
            command.name(),
            "Campus event name",
            180
        );

        requiredText(
            command.eventType(),
            "Campus event type",
            40
        );

        if (command.startsAt() == null) {
            throw new GroupEventValidationException(
                "Campus event startsAt is required."
            );
        }

        if (command.endsAt() == null) {
            throw new GroupEventValidationException(
                "Campus event endsAt is required."
            );
        }

        if (!command.endsAt().isAfter(command.startsAt())) {
            throw new GroupEventValidationException(
                "Campus event endsAt must be after startsAt."
            );
        }

        if (
            command.expectedAttendance() != null
                && command.expectedAttendance() < 0
        ) {
            throw new GroupEventValidationException(
                "Expected attendance cannot be negative."
            );
        }
    }

    private String requiredText(
        String value,
        String label,
        int maxLength
    ) {

        String normalized =
            value == null
                ? null
                : value.trim();

        if (
            normalized == null
                || normalized.isEmpty()
        ) {
            throw new GroupEventValidationException(
                label + " is required."
            );
        }

        if (normalized.length() > maxLength) {
            throw new GroupEventValidationException(
                label + " is too long."
            );
        }

        return normalized;
    }

    private String nullableText(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }

    private void requireId(
        UUID value,
        String label
    ) {

        if (value == null) {
            throw new GroupEventValidationException(
                label + " is required."
            );
        }
    }

    private CampusEventResponse response(
        java.sql.ResultSet resultSet,
        boolean replayed
    ) throws java.sql.SQLException {

        return new CampusEventResponse(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("campus_id", UUID.class),
            resultSet.getString("name"),
            resultSet.getString("event_type"),
            resultSet.getObject("starts_at", OffsetDateTime.class),
            resultSet.getObject("ends_at", OffsetDateTime.class),
            resultSet.getObject("expected_attendance", Integer.class),
            resultSet.getString("description"),
            resultSet.getObject("created_by", UUID.class),
            resultSet.getObject("created_at", OffsetDateTime.class),
            resultSet.getObject("updated_at", OffsetDateTime.class),
            replayed
        );
    }

    private CampusEventResponse replay(
        CampusEventResponse stored
    ) {

        return new CampusEventResponse(
            stored.id(),
            stored.campusId(),
            stored.name(),
            stored.eventType(),
            stored.startsAt(),
            stored.endsAt(),
            stored.expectedAttendance(),
            stored.description(),
            stored.createdBy(),
            stored.createdAt(),
            stored.updatedAt(),
            true
        );
    }

    /**
     * PostgreSQL TIMESTAMPTZ stores fractional seconds at microsecond
     * precision. Resource-ID replay therefore compares instants using
     * the precision that survives the database round-trip instead of
     * raw OffsetDateTime nanosecond equality.
     */
    private static boolean samePostgresTimestamp(
        OffsetDateTime left,
        OffsetDateTime right
    ) {

        if (
            left == null
            || right == null
        ) {
            return left == right;
        }

        java.time.Duration delta =
            java.time.Duration
                .between(
                    left.toInstant(),
                    right.toInstant()
                )
                .abs();

        return delta.compareTo(
            java.time.Duration.ofNanos(
                1_000L
            )
        ) < 0;
    }
}