package com.sup2i.food.canteen.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import com.sup2i.food.canteen.api.dto.CanteenReservationRequest;
import com.sup2i.food.canteen.api.dto.CanteenReservationResponse;
import com.sup2i.food.canteen.exception.CanteenErrorCode;
import com.sup2i.food.canteen.exception.CanteenException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CanteenReservationService {

    private static final int
        RESPONSE_STATUS_CREATED = 201;

    private static final String
        RESOURCE_TYPE =
            "CANTEEN_RESERVATION";

    private static final Duration
        IDEMPOTENCY_RETENTION =
            Duration.ofHours(24);

    private final JdbcTemplate jdbcTemplate;

    private final JsonMapper objectMapper;

    private final MealEligibilityService
        mealEligibilityService;

    public CanteenReservationService(
        JdbcTemplate jdbcTemplate,
        JsonMapper objectMapper,
        MealEligibilityService mealEligibilityService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.objectMapper =
            objectMapper;

        this.mealEligibilityService =
            mealEligibilityService;
    }

    @Transactional
    public CanteenReservationResponse reserve(
        UUID actorId,
        String rawIdempotencyKey,
        CanteenReservationRequest request
    ) {

        if (
            request == null
            || request.menuId() == null
        ) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "menuId is required."
            );
        }

        StudentContext student =
            studentContext(
                actorId
            );

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        String scope =
            "CANTEEN_RESERVE:"
                + student.organizationId();

        String requestHash =
            requestHash(
                actorId,
                student.studentId(),
                request.menuId()
            );

        OffsetDateTime now =
            OffsetDateTime.now();

        lockIdempotency(
            scope,
            idempotencyKey
        );

        deleteExpired(
            scope,
            idempotencyKey,
            now
        );

        Optional<StoredIdempotency> stored =
            findStored(
                scope,
                idempotencyKey
            );

        if (stored.isPresent()) {

            StoredIdempotency replay =
                stored.get();

            validateReplay(
                replay,
                actorId,
                requestHash
            );

            return deserializeResponse(
                replay.responseBody()
            );
        }

        MenuRow menu =
            lockMenu(
                student,
                request.menuId()
            );

        requireReservable(
            menu
        );

        mealEligibilityService
            .requireStudentEligible(
                student.organizationId(),
                student.studentId(),
                menu.id()
            );

        long duplicateCount =
            reservationCount(
                student.studentId(),
                menu.id()
            );

        if (duplicateCount > 0) {

            throw new CanteenException(
                CanteenErrorCode.CANTEEN_ALREADY_RESERVED,
                "Canteen meal is already reserved."
            );
        }

        UUID reservationId =
            UUID.randomUUID();

        OffsetDateTime reservedAt =
            OffsetDateTime.now();

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO canteen_reservations (
                    id,
                    student_id,
                    meal_beneficiary_id,
                    menu_id,
                    status,
                    reserved_at
                )
                VALUES (
                    ?,
                    ?,
                    NULL,
                    ?,
                    'RESERVED',
                    ?
                )
                """,
                reservationId,
                student.studentId(),
                menu.id(),
                reservedAt
            );

        if (inserted != 1) {

            throw new IllegalStateException(
                "Canteen reservation insert did not affect exactly one row."
            );
        }

        CanteenReservationResponse response =
            new CanteenReservationResponse(
                reservationId,
                menu.id(),
                student.studentId(),
                "RESERVED",
                reservedAt
            );

        String responseBody =
            serializeResponse(
                response
            );

        persistIdempotency(
            scope,
            idempotencyKey,
            actorId,
            requestHash,
            responseBody,
            reservationId,
            now.plus(
                IDEMPOTENCY_RETENTION
            )
        );

        return response;
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
                    s.id AS student_id,
                    u.organization_id,
                    s.campus_id
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                JOIN students s
                  ON s.user_id = u.id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                  AND s.enrollment_status = 'ACTIVE'
                  AND c.is_active = TRUE
                  AND c.organization_id =
                      u.organization_id
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
                            "campus_id",
                            UUID.class
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
                "Student context lookup returned multiple rows."
            );
        }

        return rows.get(0);
    }

    private MenuRow lockMenu(
        StudentContext student,
        UUID menuId
    ) {

        List<MenuRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    cm.id,
                    cm.menu_date,
                    cm.status,
                    c.timezone
                FROM canteen_menus cm
                JOIN locations l
                  ON l.id = cm.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE cm.id = ?
                  AND c.organization_id = ?
                  AND c.id = ?
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                FOR UPDATE OF cm
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new MenuRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "menu_date",
                            LocalDate.class
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getString(
                            "timezone"
                        )
                    ),
                menuId,
                student.organizationId(),
                student.campusId()
            );

        if (rows.isEmpty()) {

            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Canteen menu does not exist."
            );
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Canteen menu lookup returned multiple rows."
            );
        }

        return rows.get(0);
    }

    private void requireReservable(
        MenuRow menu
    ) {

        if (
            !"PUBLISHED".equals(
                menu.status()
            )
        ) {

            throw new CanteenException(
                CanteenErrorCode.CANTEEN_RESERVATION_CLOSED,
                "Canteen reservation is closed."
            );
        }

        ZoneId zoneId;

        try {

            zoneId =
                ZoneId.of(
                    menu.timezone()
                );
        }
        catch (RuntimeException exception) {

            throw new IllegalStateException(
                "Campus timezone is invalid.",
                exception
            );
        }

        LocalDate today =
            LocalDate.now(
                zoneId
            );

        if (
            menu.date()
                .isBefore(today)
        ) {

            throw new CanteenException(
                CanteenErrorCode.CANTEEN_RESERVATION_CLOSED,
                "Canteen reservation deadline has passed."
            );
        }
    }

    private long reservationCount(
        UUID studentId,
        UUID menuId
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM canteen_reservations
                WHERE student_id = ?
                  AND menu_id = ?
                """,
                Long.class,
                studentId,
                menuId
            );

        if (count == null) {
            return 0;
        }

        return count;
    }

    private String normalizeIdempotencyKey(
        String rawIdempotencyKey
    ) {

        if (rawIdempotencyKey == null) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Idempotency-Key header is required."
            );
        }

        String key =
            rawIdempotencyKey.trim();

        if (key.isBlank()) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Idempotency-Key header is required."
            );
        }

        if (key.length() < 8) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Idempotency-Key must contain at least 8 characters."
            );
        }

        if (key.length() > 160) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Idempotency-Key cannot exceed 160 characters."
            );
        }

        return key;
    }

    private void lockIdempotency(
        String scope,
        String idempotencyKey
    ) {

        byte[] digest =
            sha256Bytes(
                "CANTEEN_IDEMPOTENCY"
                    + "\n"
                    + scope
                    + "\n"
                    + idempotencyKey
            );

        long lockKey =
            ByteBuffer
                .wrap(digest)
                .getLong();

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(?)",
            statement ->
                statement.setLong(
                    1,
                    lockKey
                ),
            (ResultSetExtractor<Void>)
                resultSet ->
                    null
        );
    }

    private void deleteExpired(
        String scope,
        String idempotencyKey,
        OffsetDateTime now
    ) {

        jdbcTemplate.update(
            """
            DELETE FROM idempotency_records
            WHERE scope = ?
              AND idempotency_key = ?
              AND expires_at <= ?
            """,
            scope,
            idempotencyKey,
            now
        );
    }

    private Optional<StoredIdempotency>
        findStored(
            String scope,
            String idempotencyKey
        ) {

        List<StoredIdempotency> rows =
            jdbcTemplate.query(
                """
                SELECT
                    user_id,
                    request_hash,
                    response_status,
                    response_body::text
                        AS response_body,
                    resource_type,
                    resource_id
                FROM idempotency_records
                WHERE scope = ?
                  AND idempotency_key = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new StoredIdempotency(
                        resultSet.getObject(
                            "user_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "request_hash"
                        ),
                        resultSet.getInt(
                            "response_status"
                        ),
                        resultSet.getString(
                            "response_body"
                        ),
                        resultSet.getString(
                            "resource_type"
                        ),
                        resultSet.getObject(
                            "resource_id",
                            UUID.class
                        )
                    ),
                scope,
                idempotencyKey
            );

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Idempotency lookup returned multiple rows."
            );
        }

        return Optional.of(
            rows.get(0)
        );
    }

    private void validateReplay(
        StoredIdempotency stored,
        UUID actorId,
        String requestHash
    ) {

        boolean sameUser =
            actorId.equals(
                stored.userId()
            );

        boolean sameRequest =
            requestHash.equals(
                stored.requestHash()
            );

        boolean sameStatus =
            stored.responseStatus()
                == RESPONSE_STATUS_CREATED;

        boolean sameResourceType =
            RESOURCE_TYPE.equals(
                stored.resourceType()
            );

        boolean hasResource =
            stored.resourceId()
                != null;

        boolean hasResponse =
            stored.responseBody()
                != null;

        boolean validReplay =
            sameUser
                && sameRequest
                && sameStatus
                && sameResourceType
                && hasResource
                && hasResponse;

        if (!validReplay) {

            throw new CanteenException(
                CanteenErrorCode.IDEMPOTENCY_CONFLICT,
                "Idempotency-Key was already used for another request."
            );
        }
    }

    private void persistIdempotency(
        String scope,
        String idempotencyKey,
        UUID actorId,
        String requestHash,
        String responseBody,
        UUID reservationId,
        OffsetDateTime expiresAt
    ) {

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO idempotency_records (
                    id,
                    idempotency_key,
                    scope,
                    user_id,
                    request_hash,
                    response_status,
                    response_body,
                    resource_type,
                    resource_id,
                    expires_at
                )
                VALUES (
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
                UUID.randomUUID(),
                idempotencyKey,
                scope,
                actorId,
                requestHash,
                RESPONSE_STATUS_CREATED,
                responseBody,
                RESOURCE_TYPE,
                reservationId,
                expiresAt
            );

        if (inserted != 1) {

            throw new IllegalStateException(
                "Idempotency insert did not affect exactly one row."
            );
        }
    }

    private String serializeResponse(
        CanteenReservationResponse response
    ) {

        try {

            return objectMapper
                .writeValueAsString(
                    response
                );
        }
        catch (JacksonException exception) {

            throw new IllegalStateException(
                "Unable to serialize canteen reservation response.",
                exception
            );
        }
    }

    private CanteenReservationResponse
        deserializeResponse(
            String responseBody
        ) {

        try {

            return objectMapper
                .readerFor(
                    CanteenReservationResponse.class
                )
                .without(
                    DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
                )
                .readValue(
                    responseBody
                );
        }
        catch (JacksonException exception) {

            throw new IllegalStateException(
                "Unable to deserialize canteen reservation response.",
                exception
            );
        }
    }

    private String requestHash(
        UUID actorId,
        UUID studentId,
        UUID menuId
    ) {

        String canonical =
            "CANTEEN_RESERVE"
                + "\n"
                + actorId
                + "\n"
                + studentId
                + "\n"
                + menuId;

        byte[] digest =
            sha256Bytes(
                canonical
            );

        StringBuilder hex =
            new StringBuilder(
                digest.length * 2
            );

        for (byte value : digest) {

            hex.append(
                String.format(
                    "%02x",
                    value & 0xff
                )
            );
        }

        return hex.toString();
    }

    private byte[] sha256Bytes(
        String value
    ) {

        try {

            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            return digest.digest(
                value.getBytes(
                    StandardCharsets.UTF_8
                )
            );
        }
        catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                "SHA-256 is unavailable.",
                exception
            );
        }
    }

    private record StudentContext(
        UUID studentId,
        UUID organizationId,
        UUID campusId
    ) {
    }

    private record MenuRow(
        UUID id,
        LocalDate date,
        String status,
        String timezone
    ) {
    }

    private record StoredIdempotency(
        UUID userId,
        String requestHash,
        int responseStatus,
        String responseBody,
        String resourceType,
        UUID resourceId
    ) {
    }
}
