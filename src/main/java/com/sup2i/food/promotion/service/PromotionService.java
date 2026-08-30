package com.sup2i.food.promotion.service;

import com.sup2i.food.promotion.api.dto.PromotionResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PromotionService {

    private final JdbcTemplate jdbcTemplate;

    public PromotionService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> listActive(
        UUID actorId,
        UUID locationId
    ) {

        ActorContext actor =
            actorContext(
                actorId
            );

        LocationContext location =
            locationContext(
                actor.organizationId(),
                locationId
            );

        if (location == null) {
            return List.of();
        }

        ZonedDateTime campusNow;

        try {

            campusNow =
                ZonedDateTime.now(
                    ZoneId.of(
                        location.timezone()
                    )
                );
        }
        catch (DateTimeException exception) {

            throw new IllegalStateException(
                "Invalid campus timezone configuration.",
                exception
            );
        }

        OffsetDateTime absoluteNow =
            campusNow.toOffsetDateTime();

        List<PromotionCandidate> candidates =
            jdbcTemplate.query(
                """
                SELECT
                    p.id,
                    p.name,
                    p.type,
                    p.starts_at,
                    p.ends_at,
                    p.stackable,
                    p.priority
                FROM promotions p
                WHERE p.organization_id = ?
                  AND p.status = 'ACTIVE'
                  AND p.mobile_enabled = TRUE
                  AND p.starts_at <= ?
                  AND p.ends_at > ?
                ORDER BY
                    p.priority DESC,
                    p.starts_at ASC,
                    p.id ASC
                """,
                this::mapCandidate,
                actor.organizationId(),
                absoluteNow,
                absoluteNow
            );

        return candidates
            .stream()
            .filter(
                candidate ->
                    scheduleAllows(
                        candidate.id(),
                        campusNow
                    )
            )
            .filter(
                candidate ->
                    audienceAllows(
                        candidate.id(),
                        locationId,
                        actor.studentId(),
                        absoluteNow
                    )
            )
            .map(
                PromotionCandidate::response
            )
            .toList();
    }

    private ActorContext actorContext(
        UUID actorId
    ) {

        List<ActorContext> actors =
            jdbcTemplate.query(
                """
                SELECT
                    u.organization_id,
                    s.id AS student_id
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                LEFT JOIN students s
                  ON s.user_id = u.id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new ActorContext(
                        resultSet.getObject(
                            "organization_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "student_id",
                            UUID.class
                        )
                    ),
                actorId
            );

        if (actors.isEmpty()) {

            throw new BadCredentialsException(
                "Authenticated user is not active."
            );
        }

        if (actors.size() != 1) {

            throw new IllegalStateException(
                "Authenticated user lookup returned multiple rows."
            );
        }

        return actors.get(0);
    }

    private LocationContext locationContext(
        UUID organizationId,
        UUID locationId
    ) {

        List<LocationContext> locations =
            jdbcTemplate.query(
                """
                SELECT
                    l.id,
                    c.timezone
                FROM locations l
                JOIN campuses c
                  ON c.id = l.campus_id
                JOIN organizations o
                  ON o.id = c.organization_id
                WHERE l.id = ?
                  AND c.organization_id = ?
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                  AND o.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new LocationContext(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "timezone"
                        )
                    ),
                locationId,
                organizationId
            );

        if (locations.isEmpty()) {
            return null;
        }

        if (locations.size() != 1) {

            throw new IllegalStateException(
                "Location lookup returned multiple rows."
            );
        }

        return locations.get(0);
    }

    private boolean scheduleAllows(
        UUID promotionId,
        ZonedDateTime campusNow
    ) {

        List<ScheduleWindow> windows =
            jdbcTemplate.query(
                """
                SELECT
                    day_of_week,
                    starts_at_time,
                    ends_at_time
                FROM promotion_schedule_windows
                WHERE promotion_id = ?
                ORDER BY id
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new ScheduleWindow(
                        resultSet.getObject(
                            "day_of_week",
                            Integer.class
                        ),
                        resultSet.getObject(
                            "starts_at_time",
                            java.time.LocalTime.class
                        ),
                        resultSet.getObject(
                            "ends_at_time",
                            java.time.LocalTime.class
                        )
                    ),
                promotionId
            );

        if (windows.isEmpty()) {
            return true;
        }

        int day =
            campusNow
                .getDayOfWeek()
                .getValue();

        java.time.LocalTime time =
            campusNow.toLocalTime();

        for (ScheduleWindow window : windows) {

            boolean dayMatches =
                window.dayOfWeek() == null
                    || window.dayOfWeek() == day;

            boolean startMatches =
                window.startsAt() == null
                    || !time.isBefore(
                        window.startsAt()
                    );

            boolean endMatches =
                window.endsAt() == null
                    || time.isBefore(
                        window.endsAt()
                    );

            if (
                dayMatches
                    && startMatches
                    && endMatches
            ) {
                return true;
            }
        }

        return false;
    }

    private boolean audienceAllows(
        UUID promotionId,
        UUID locationId,
        UUID studentId,
        OffsetDateTime now
    ) {

        List<Target> targets =
            jdbcTemplate.query(
                """
                SELECT
                    target_type,
                    target_id,
                    include_target
                FROM promotion_targets
                WHERE promotion_id = ?
                ORDER BY id
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new Target(
                        resultSet.getString(
                            "target_type"
                        ),
                        resultSet.getObject(
                            "target_id",
                            UUID.class
                        ),
                        resultSet.getBoolean(
                            "include_target"
                        )
                    ),
                promotionId
            );

        if (targets.isEmpty()) {
            return true;
        }

        boolean hasIncludeTarget =
            false;

        boolean includeMatched =
            false;

        for (Target target : targets) {

            boolean matches =
                targetMatches(
                    target,
                    locationId,
                    studentId,
                    now
                );

            if (!target.includeTarget()) {

                if (matches) {
                    return false;
                }

                continue;
            }

            hasIncludeTarget =
                true;

            if (matches) {
                includeMatched = true;
            }
        }

        return !hasIncludeTarget
            || includeMatched;
    }

    private boolean targetMatches(
        Target target,
        UUID locationId,
        UUID studentId,
        OffsetDateTime now
    ) {

        return switch (target.type()) {

            case "ALL" ->
                true;

            case "LOCATION" ->
                target.id() != null
                    && target.id().equals(
                        locationId
                    );

            case "STUDENT" ->
                studentId != null
                    && target.id() != null
                    && target.id().equals(
                        studentId
                    );

            case "STUDENT_SEGMENT" ->
                studentId != null
                    && target.id() != null
                    && activeSegmentMembership(
                        target.id(),
                        studentId,
                        now
                    );

            /*
             * The listing contract has no cart/product/menu input.
             * Product/category/menu targeting is therefore potentially
             * applicable at discovery time. Exact item applicability
             * belongs to the future pricing engine.
             */
            case "PRODUCT",
                 "CATEGORY",
                 "MENU" ->
                true;

            default ->
                false;
        };
    }

    private boolean activeSegmentMembership(
        UUID segmentId,
        UUID studentId,
        OffsetDateTime now
    ) {

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM student_segment_memberships ssm
                JOIN student_segments ss
                  ON ss.id = ssm.segment_id
                WHERE ssm.segment_id = ?
                  AND ssm.student_id = ?
                  AND ss.is_active = TRUE
                  AND ssm.valid_from <= ?
                  AND (
                        ssm.valid_to IS NULL
                        OR ssm.valid_to > ?
                  )
                """,
                Integer.class,
                segmentId,
                studentId,
                now,
                now
            );

        return count != null
            && count > 0;
    }

    private PromotionCandidate mapCandidate(
        ResultSet resultSet,
        int rowNumber
    )
        throws SQLException {

        PromotionResponse response =
            new PromotionResponse(
                resultSet.getObject(
                    "id",
                    UUID.class
                ),
                resultSet.getString(
                    "name"
                ),
                resultSet.getString(
                    "type"
                ),
                resultSet.getObject(
                    "starts_at",
                    OffsetDateTime.class
                ),
                resultSet.getObject(
                    "ends_at",
                    OffsetDateTime.class
                ),
                resultSet.getBoolean(
                    "stackable"
                )
            );

        return new PromotionCandidate(
            response,
            resultSet.getInt(
                "priority"
            )
        );
    }

    private record ActorContext(
        UUID organizationId,
        UUID studentId
    ) {
    }

    private record LocationContext(
        UUID locationId,
        String timezone
    ) {
    }

    private record Target(
        String type,
        UUID id,
        boolean includeTarget
    ) {
    }

    private record ScheduleWindow(
        Integer dayOfWeek,
        java.time.LocalTime startsAt,
        java.time.LocalTime endsAt
    ) {
    }

    private record PromotionCandidate(
        PromotionResponse response,
        int priority
    ) {

        UUID id() {
            return response.id();
        }
    }
}
