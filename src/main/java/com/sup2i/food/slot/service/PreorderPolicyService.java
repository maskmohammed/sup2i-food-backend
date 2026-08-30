package com.sup2i.food.slot.service;

import com.sup2i.food.slot.exception.TimeSlotErrorCode;
import com.sup2i.food.slot.exception.TimeSlotException;
import org.springframework.jdbc.core.JdbcTemplate;
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
public class PreorderPolicyService {

    private final JdbcTemplate jdbcTemplate;

    public PreorderPolicyService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public void requireOperationalWindow(
        UUID locationId,
        UUID campusId,
        String campusTimezone,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime
    ) {

        ZoneId zoneId =
            zoneId(
                campusTimezone
            );

        ZonedDateTime startsAt =
            ZonedDateTime.of(
                slotDate,
                startTime,
                zoneId
            );

        ZonedDateTime endsAt =
            ZonedDateTime.of(
                slotDate,
                endTime,
                zoneId
            );

        ZonedDateTime now =
            ZonedDateTime.now(
                zoneId
            );

        if (!startsAt.isAfter(now)) {

            throw unavailable(
                "Time slot booking cutoff has passed."
            );
        }

        ScheduleRule exception =
            scheduleException(
                locationId,
                slotDate
            );

        if (exception != null) {

            requireInsideRule(
                exception,
                startTime,
                endTime,
                "Time slot is unavailable because of a location schedule exception."
            );
        }
        else {

            ScheduleRule businessHours =
                businessHours(
                    locationId,
                    slotDate
                );

            if (businessHours != null) {

                requireInsideRule(
                    businessHours,
                    startTime,
                    endTime,
                    "Time slot is outside location business hours."
                );
            }
        }

        requireNoAcademicClosure(
            campusId,
            slotDate,
            startsAt.toOffsetDateTime(),
            endsAt.toOffsetDateTime()
        );
    }

    @Transactional(readOnly = true)
    public boolean isOperationallyOpen(
        UUID locationId,
        UUID campusId,
        String campusTimezone,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime
    ) {

        try {

            requireOperationalWindow(
                locationId,
                campusId,
                campusTimezone,
                slotDate,
                startTime,
                endTime
            );

            return true;
        }
        catch (TimeSlotException exception) {

            return false;
        }
    }

    @Transactional(readOnly = true)
    public OffsetDateTime paymentDeadline(
        UUID locationId,
        UUID campusId,
        String campusTimezone,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime
    ) {

        requireOperationalWindow(
            locationId,
            campusId,
            campusTimezone,
            slotDate,
            startTime,
            endTime
        );

        ZoneId zoneId =
            zoneId(
                campusTimezone
            );

        ZonedDateTime deadline =
            ZonedDateTime.of(
                slotDate,
                startTime,
                zoneId
            );

        return deadline.toOffsetDateTime();
    }

    private ScheduleRule scheduleException(
        UUID locationId,
        LocalDate date
    ) {

        List<ScheduleRule> rules =
            jdbcTemplate.query(
                """
                SELECT
                    is_closed,
                    opens_at,
                    closes_at
                FROM location_schedule_exceptions
                WHERE location_id = ?
                  AND exception_date = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new ScheduleRule(
                        resultSet.getBoolean(
                            "is_closed"
                        ),
                        resultSet.getObject(
                            "opens_at",
                            LocalTime.class
                        ),
                        resultSet.getObject(
                            "closes_at",
                            LocalTime.class
                        )
                    ),
                locationId,
                date
            );

        if (rules.isEmpty()) {
            return null;
        }

        if (rules.size() != 1) {

            throw new IllegalStateException(
                "Location schedule exception lookup returned multiple rows."
            );
        }

        return rules.get(0);
    }

    private ScheduleRule businessHours(
        UUID locationId,
        LocalDate date
    ) {

        int dayOfWeek =
            date
                .getDayOfWeek()
                .getValue();

        List<ScheduleRule> rules =
            jdbcTemplate.query(
                """
                SELECT
                    is_closed,
                    opens_at,
                    closes_at
                FROM location_business_hours
                WHERE location_id = ?
                  AND day_of_week = ?
                  AND (
                      valid_from IS NULL
                      OR valid_from <= ?
                  )
                  AND (
                      valid_to IS NULL
                      OR valid_to >= ?
                  )
                ORDER BY
                    CASE
                        WHEN valid_from IS NULL
                            THEN 1
                        ELSE 0
                    END ASC,
                    valid_from DESC NULLS LAST,
                    CASE
                        WHEN valid_to IS NULL
                            THEN 1
                        ELSE 0
                    END ASC,
                    valid_to ASC NULLS LAST,
                    created_at DESC,
                    id DESC
                LIMIT 1
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new ScheduleRule(
                        resultSet.getBoolean(
                            "is_closed"
                        ),
                        resultSet.getObject(
                            "opens_at",
                            LocalTime.class
                        ),
                        resultSet.getObject(
                            "closes_at",
                            LocalTime.class
                        )
                    ),
                locationId,
                dayOfWeek,
                date,
                date
            );

        if (rules.isEmpty()) {
            return null;
        }

        return rules.get(0);
    }

    private void requireInsideRule(
        ScheduleRule rule,
        LocalTime slotStart,
        LocalTime slotEnd,
        String message
    ) {

        if (rule.closed()) {
            throw unavailable(message);
        }

        boolean missingTimes =
            rule.opensAt() == null
                || rule.closesAt() == null;

        if (missingTimes) {

            throw new IllegalStateException(
                "Open schedule rule must define opens_at and closes_at."
            );
        }

        boolean tooEarly =
            slotStart.isBefore(
                rule.opensAt()
            );

        boolean tooLate =
            slotEnd.isAfter(
                rule.closesAt()
            );

        boolean outside =
            tooEarly || tooLate;

        if (outside) {
            throw unavailable(message);
        }
    }

    private void requireNoAcademicClosure(
        UUID campusId,
        LocalDate slotDate,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
    ) {

        Long closures =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM academic_calendar_events ace
                JOIN academic_calendars ac
                  ON ac.id = ace.academic_calendar_id
                WHERE ac.campus_id = ?
                  AND ac.is_active = TRUE
                  AND ? BETWEEN
                      ac.starts_on
                      AND ac.ends_on
                  AND ace.affects_service = TRUE
                  AND ace.event_type IN (
                      'HOLIDAY',
                      'VACATION',
                      'CLOSURE'
                  )
                  AND ace.starts_at < ?
                  AND ace.ends_at > ?
                """,
                Long.class,
                campusId,
                slotDate,
                endsAt,
                startsAt
            );

        boolean closed =
            closures != null
                && closures > 0;

        if (closed) {

            throw unavailable(
                "Time slot is unavailable because campus food service is closed."
            );
        }
    }

    private ZoneId zoneId(
        String timezone
    ) {

        boolean missing =
            timezone == null
                || timezone.isBlank();

        if (missing) {

            throw new IllegalStateException(
                "Campus timezone is missing."
            );
        }

        try {

            return ZoneId.of(
                timezone
            );
        }
        catch (RuntimeException exception) {

            throw new IllegalStateException(
                "Campus timezone is invalid.",
                exception
            );
        }
    }

    private TimeSlotException unavailable(
        String message
    ) {

        return new TimeSlotException(
            TimeSlotErrorCode.SLOT_CLOSED,
            message
        );
    }

    private record ScheduleRule(
        boolean closed,
        LocalTime opensAt,
        LocalTime closesAt
    ) {
    }
}