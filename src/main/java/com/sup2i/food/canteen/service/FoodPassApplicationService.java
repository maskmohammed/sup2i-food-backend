package com.sup2i.food.canteen.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import com.sup2i.food.canteen.api.dto.FoodPassResponse;
import com.sup2i.food.canteen.exception.CanteenErrorCode;
import com.sup2i.food.canteen.exception.CanteenException;
import com.sup2i.food.security.api.dto.StudentSummaryResponse;

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
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FoodPassApplicationService {

    private static final int
        RESPONSE_STATUS_OK =
            200;

    private static final String
        RESOURCE_TYPE =
            "FOOD_PASS";

    private static final Duration
        IDEMPOTENCY_RETENTION =
            Duration.ofHours(24);

    private final JdbcTemplate jdbcTemplate;

    private final JsonMapper objectMapper;

    public FoodPassApplicationService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.objectMapper =
            JsonMapper.builder()
                .enable(
                    DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS
                )
                .build();
    }

    @Transactional(readOnly = true)
    public FoodPassResponse mine(
        UUID actorId
    ) {

        StudentActor actor =
            studentActor(
                actorId
            );

        List<FoodPassRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    fp.id,
                    fp.card_number,
                    CASE
                        WHEN fp.status = 'ACTIVE'
                         AND fp.expires_at IS NOT NULL
                         AND fp.expires_at
                             <= CURRENT_TIMESTAMP
                        THEN 'EXPIRED'
                        ELSE fp.status
                    END AS effective_status,
                    fp.expires_at,
                    s.id AS student_id,
                    s.student_number,
                    s.program,
                    s.level,
                    s.group_name,
                    (
                        SELECT sp.photo_url
                        FROM student_photos sp
                        WHERE sp.student_id = s.id
                          AND sp.is_current = TRUE
                          AND sp.revoked_at IS NULL
                        ORDER BY
                            sp.created_at DESC,
                            sp.id DESC
                        LIMIT 1
                    ) AS photo_url
                FROM food_passes fp
                JOIN students s
                  ON s.id = fp.student_id
                JOIN users u
                  ON u.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE fp.student_id = ?
                  AND u.id = ?
                  AND u.organization_id = ?
                  AND c.organization_id = ?
                  AND fp.status <> 'PENDING_ISSUE'
                ORDER BY
                    fp.issued_at DESC,
                    fp.created_at DESC,
                    fp.id DESC
                LIMIT 1
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    row(
                        resultSet
                    ),
                actor.studentId(),
                actor.userId(),
                actor.organizationId(),
                actor.organizationId()
            );

        if (rows.isEmpty()) {

            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Food Pass does not exist for this student."
            );
        }

        return response(
            rows.get(0)
        );
    }

    @Transactional
    public FoodPassResponse reportLost(
        UUID actorId,
        String rawIdempotencyKey,
        UUID foodPassId
    ) {

        requireIdentifier(
            foodPassId,
            "foodPassId"
        );

        Actor actor =
            actor(
                actorId
            );

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        String scope =
            "FOOD_PASS_REPORT_LOST:"
                + actor.organizationId();

        String requestHash =
            requestHash(
                actorId,
                foodPassId
            );

        OffsetDateTime now =
            OffsetDateTime.now();

        lockIdempotency(
            scope,
            idempotencyKey
        );

        deleteExpiredIdempotency(
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

            return deserialize(
                replay.responseBody()
            );
        }

        FoodPassMutationRow pass =
            lockOwnedPass(
                actor,
                foodPassId
            );

        if (
            !"ACTIVE".equals(
                pass.status()
            )
        ) {

            throw stateException(
                pass.status()
            );
        }

        int passUpdated =
            jdbcTemplate.update(
                """
                UPDATE food_passes
                SET
                    status = 'LOST',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'ACTIVE'
                """,
                pass.id()
            );

        if (passUpdated != 1) {
            throw new IllegalStateException(
                "Food Pass lost transition did not affect exactly one row."
            );
        }

        int credentialUpdated =
            jdbcTemplate.update(
                """
                UPDATE qr_credentials
                SET
                    status = 'REVOKED',
                    revoked_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND credential_type = 'FOOD_PASS'
                  AND subject_id = ?
                  AND status = 'ACTIVE'
                """,
                pass.credentialId(),
                pass.id()
            );

        if (credentialUpdated != 1) {
            throw new IllegalStateException(
                "Food Pass credential revocation did not affect exactly one row."
            );
        }

        insertEvent(
            pass.id(),
            "LOST",
            "Food Pass reported lost.",
            actorId
        );

        insertAudit(
            actor.organizationId(),
            actorId,
            "FOOD_PASS_REPORTED_LOST",
            pass.id(),
            "ACTIVE",
            "LOST",
            "Food Pass reported lost."
        );

        FoodPassResponse response =
            responseById(
                actor.organizationId(),
                pass.id()
            );

        persistIdempotency(
            scope,
            idempotencyKey,
            actorId,
            requestHash,
            serialize(response),
            pass.id(),
            now.plus(
                IDEMPOTENCY_RETENTION
            )
        );

        return response;
    }

    @Transactional
    public FoodPassResponse block(
        UUID actorId,
        UUID foodPassId,
        String reason
    ) {

        requireIdentifier(
            foodPassId,
            "foodPassId"
        );

        String normalizedReason =
            normalizeReason(
                reason,
                "Block reason is required."
            );

        Actor actor =
            actor(
                actorId
            );

        FoodPassMutationRow pass =
            lockAdministrativePass(
                actor,
                foodPassId
            );

        if (
            "BLOCKED".equals(
                pass.status()
            )
        ) {

            return responseById(
                actor.organizationId(),
                pass.id()
            );
        }

        if (
            !"ACTIVE".equals(
                pass.status()
            )
        ) {

            throw stateException(
                pass.status()
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE food_passes
                SET
                    status = 'BLOCKED',
                    blocked_at = CURRENT_TIMESTAMP,
                    block_reason = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'ACTIVE'
                """,
                normalizedReason,
                pass.id()
            );

        if (updated != 1) {
            throw new IllegalStateException(
                "Food Pass block transition failed."
            );
        }

        insertEvent(
            pass.id(),
            "BLOCKED",
            normalizedReason,
            actorId
        );

        insertAudit(
            actor.organizationId(),
            actorId,
            "FOOD_PASS_BLOCKED",
            pass.id(),
            "ACTIVE",
            "BLOCKED",
            normalizedReason
        );

        return responseById(
            actor.organizationId(),
            pass.id()
        );
    }

    @Transactional
    public FoodPassResponse unblock(
        UUID actorId,
        UUID foodPassId
    ) {

        requireIdentifier(
            foodPassId,
            "foodPassId"
        );

        Actor actor =
            actor(
                actorId
            );

        FoodPassMutationRow pass =
            lockAdministrativePass(
                actor,
                foodPassId
            );

        if (
            "ACTIVE".equals(
                pass.status()
            )
        ) {

            return responseById(
                actor.organizationId(),
                pass.id()
            );
        }

        if (
            !"BLOCKED".equals(
                pass.status()
            )
        ) {

            throw stateException(
                pass.status()
            );
        }

        if (
            pass.expiresAt() != null
            && !OffsetDateTime.now()
                .isBefore(
                    pass.expiresAt()
                )
        ) {

            throw new CanteenException(
                CanteenErrorCode.FOOD_PASS_EXPIRED,
                "Expired Food Pass cannot be unblocked."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE food_passes
                SET
                    status = 'ACTIVE',
                    blocked_at = NULL,
                    block_reason = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'BLOCKED'
                """,
                pass.id()
            );

        if (updated != 1) {
            throw new IllegalStateException(
                "Food Pass unblock transition failed."
            );
        }

        insertEvent(
            pass.id(),
            "REACTIVATED",
            "Food Pass unblocked.",
            actorId
        );

        insertAudit(
            actor.organizationId(),
            actorId,
            "FOOD_PASS_UNBLOCKED",
            pass.id(),
            "BLOCKED",
            "ACTIVE",
            "Food Pass unblocked."
        );

        return responseById(
            actor.organizationId(),
            pass.id()
        );
    }

    private FoodPassMutationRow lockOwnedPass(
        Actor actor,
        UUID foodPassId
    ) {

        List<FoodPassMutationRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    fp.id,
                    fp.student_id,
                    fp.credential_id,
                    fp.status,
                    fp.expires_at
                FROM food_passes fp
                JOIN students s
                  ON s.id = fp.student_id
                JOIN users student_user
                  ON student_user.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE fp.id = ?
                  AND student_user.organization_id = ?
                  AND c.organization_id = ?
                  AND (
                        ? IS NULL
                        OR fp.student_id = ?
                  )
                FOR UPDATE OF fp
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new FoodPassMutationRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "student_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "credential_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "expires_at",
                            OffsetDateTime.class
                        )
                    ),
                foodPassId,
                actor.organizationId(),
                actor.organizationId(),
                actor.studentId(),
                actor.studentId()
            );

        if (rows.isEmpty()) {

            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Food Pass does not exist."
            );
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Food Pass lookup returned multiple rows."
            );
        }

        return rows.get(0);
    }

    private FoodPassMutationRow lockAdministrativePass(
        Actor actor,
        UUID foodPassId
    ) {

        List<FoodPassMutationRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    fp.id,
                    fp.student_id,
                    fp.credential_id,
                    fp.status,
                    fp.expires_at
                FROM food_passes fp
                JOIN students s
                  ON s.id = fp.student_id
                JOIN users student_user
                  ON student_user.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE fp.id = ?
                  AND student_user.organization_id = ?
                  AND c.organization_id = ?
                FOR UPDATE OF fp
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new FoodPassMutationRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "student_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "credential_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "expires_at",
                            OffsetDateTime.class
                        )
                    ),
                foodPassId,
                actor.organizationId(),
                actor.organizationId()
            );

        if (rows.isEmpty()) {

            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Food Pass does not exist."
            );
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Food Pass lookup returned multiple rows."
            );
        }

        return rows.get(0);
    }

    private FoodPassResponse responseById(
        UUID organizationId,
        UUID foodPassId
    ) {

        List<FoodPassRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    fp.id,
                    fp.card_number,
                    CASE
                        WHEN fp.status = 'ACTIVE'
                         AND fp.expires_at IS NOT NULL
                         AND fp.expires_at
                             <= CURRENT_TIMESTAMP
                        THEN 'EXPIRED'
                        ELSE fp.status
                    END AS effective_status,
                    fp.expires_at,
                    s.id AS student_id,
                    s.student_number,
                    s.program,
                    s.level,
                    s.group_name,
                    (
                        SELECT sp.photo_url
                        FROM student_photos sp
                        WHERE sp.student_id = s.id
                          AND sp.is_current = TRUE
                          AND sp.revoked_at IS NULL
                        ORDER BY
                            sp.created_at DESC,
                            sp.id DESC
                        LIMIT 1
                    ) AS photo_url
                FROM food_passes fp
                JOIN students s
                  ON s.id = fp.student_id
                JOIN users u
                  ON u.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE fp.id = ?
                  AND u.organization_id = ?
                  AND c.organization_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    row(
                        resultSet
                    ),
                foodPassId,
                organizationId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Food Pass does not exist."
            );
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Food Pass response lookup returned multiple rows."
            );
        }

        return response(
            rows.get(0)
        );
    }

    private FoodPassRow row(
        java.sql.ResultSet resultSet
    ) throws java.sql.SQLException {

        return new FoodPassRow(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getString(
                "card_number"
            ),
            resultSet.getString(
                "effective_status"
            ),
            resultSet.getObject(
                "expires_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "student_id",
                UUID.class
            ),
            resultSet.getString(
                "student_number"
            ),
            resultSet.getString(
                "program"
            ),
            resultSet.getString(
                "level"
            ),
            resultSet.getString(
                "group_name"
            ),
            resultSet.getString(
                "photo_url"
            )
        );
    }

    private FoodPassResponse response(
        FoodPassRow row
    ) {

        StudentSummaryResponse student =
            new StudentSummaryResponse(
                row.studentId(),
                row.studentNumber(),
                row.program(),
                row.level(),
                row.groupName(),
                row.photoUrl()
            );

        return new FoodPassResponse(
            row.id(),
            row.cardNumber(),
            row.status(),
            row.expiresAt(),
            student
        );
    }

    private Actor actor(
        UUID actorId
    ) {

        requireIdentifier(
            actorId,
            "actorId"
        );

        List<Actor> rows =
            jdbcTemplate.query(
                """
                SELECT
                    u.id,
                    u.organization_id,
                    s.id AS student_id
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                LEFT JOIN students s
                  ON s.user_id = u.id
                 AND s.enrollment_status = 'ACTIVE'
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new Actor(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
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

        if (rows.isEmpty()) {

            throw new BadCredentialsException(
                "Authenticated user is inactive or does not exist."
            );
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Actor lookup returned multiple rows."
            );
        }

        return rows.get(0);
    }

    private StudentActor studentActor(
        UUID actorId
    ) {

        Actor actor =
            actor(
                actorId
            );

        if (actor.studentId() == null) {

            throw new BadCredentialsException(
                "Authenticated user is not an active student."
            );
        }

        return new StudentActor(
            actor.userId(),
            actor.organizationId(),
            actor.studentId()
        );
    }

    private CanteenException stateException(
        String status
    ) {

        if ("LOST".equals(status)) {

            return new CanteenException(
                CanteenErrorCode.FOOD_PASS_LOST,
                "Food Pass is already lost."
            );
        }

        if ("BLOCKED".equals(status)) {

            return new CanteenException(
                CanteenErrorCode.FOOD_PASS_BLOCKED,
                "Food Pass is blocked."
            );
        }

        if ("REVOKED".equals(status)) {

            return new CanteenException(
                CanteenErrorCode.FOOD_PASS_REVOKED,
                "Food Pass is revoked."
            );
        }

        if ("EXPIRED".equals(status)) {

            return new CanteenException(
                CanteenErrorCode.FOOD_PASS_EXPIRED,
                "Food Pass is expired."
            );
        }

        if ("REPLACED".equals(status)) {

            return new CanteenException(
                CanteenErrorCode.FOOD_PASS_REVOKED,
                "Food Pass has been replaced."
            );
        }

        return new CanteenException(
            CanteenErrorCode.VALIDATION_ERROR,
            "Food Pass transition is not allowed from status "
                + status
                + "."
        );
    }

    private void insertEvent(
        UUID foodPassId,
        String eventType,
        String reason,
        UUID actorId
    ) {

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO food_pass_events(
                    id,
                    food_pass_id,
                    event_type,
                    reason,
                    performed_by
                )
                VALUES(
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                UUID.randomUUID(),
                foodPassId,
                eventType,
                reason,
                actorId
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Food Pass event insert failed."
            );
        }
    }

    private void insertAudit(
        UUID organizationId,
        UUID actorId,
        String action,
        UUID foodPassId,
        String beforeStatus,
        String afterStatus,
        String reason
    ) {

        int inserted =
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
                    ?,
                    'FOOD_PASS',
                    ?,
                    jsonb_build_object(
                        'status',
                        ?
                    ),
                    jsonb_build_object(
                        'status',
                        ?
                    ),
                    ?,
                    'BACKEND',
                    'SUCCESS'
                )
                """,
                organizationId,
                actorId,
                action,
                foodPassId,
                beforeStatus,
                afterStatus,
                reason
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Food Pass audit insert failed."
            );
        }
    }

    private String normalizeIdempotencyKey(
        String rawKey
    ) {

        if (rawKey == null) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Idempotency-Key header is required."
            );
        }

        String key =
            rawKey.trim();

        if (
            key.length() < 8
            || key.length() > 160
        ) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Idempotency-Key length must be between 8 and 160 characters."
            );
        }

        return key;
    }

    private String normalizeReason(
        String rawReason,
        String message
    ) {

        if (rawReason == null) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                message
            );
        }

        String reason =
            rawReason.trim();

        if (reason.isEmpty()) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                message
            );
        }

        return reason;
    }

    private void requireIdentifier(
        UUID id,
        String name
    ) {

        if (id == null) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                name + " is required."
            );
        }
    }

    private String requestHash(
        UUID actorId,
        UUID foodPassId
    ) {

        String payload =
            actorId
                + "\n"
                + foodPassId;

        return sha256Hex(
            payload
        );
    }

    private void lockIdempotency(
        String scope,
        String idempotencyKey
    ) {

        byte[] digest =
            sha256Bytes(
                "FOOD_PASS_IDEMPOTENCY"
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

    private void deleteExpiredIdempotency(
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

    private Optional<StoredIdempotency> findStored(
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
                "Food Pass idempotency lookup returned multiple rows."
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

        boolean valid =
            actorId.equals(
                stored.userId()
            )
                && requestHash.equals(
                    stored.requestHash()
                )
                && stored.responseStatus()
                    == RESPONSE_STATUS_OK
                && RESOURCE_TYPE.equals(
                    stored.resourceType()
                )
                && stored.resourceId()
                    != null
                && stored.responseBody()
                    != null;

        if (!valid) {

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
        UUID resourceId,
        OffsetDateTime expiresAt
    ) {

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO idempotency_records(
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
                VALUES(
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
                RESPONSE_STATUS_OK,
                responseBody,
                RESOURCE_TYPE,
                resourceId,
                expiresAt
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Food Pass idempotency insert failed."
            );
        }
    }

    private String serialize(
        FoodPassResponse response
    ) {

        try {

            return objectMapper
                .writeValueAsString(
                    response
                );
        }
        catch (JacksonException exception) {

            throw new IllegalStateException(
                "Unable to serialize Food Pass response.",
                exception
            );
        }
    }

    private FoodPassResponse deserialize(
        String json
    ) {

        try {

            return objectMapper
                .readValue(
                    json,
                    FoodPassResponse.class
                );
        }
        catch (JacksonException exception) {

            throw new IllegalStateException(
                "Unable to deserialize Food Pass replay response.",
                exception
            );
        }
    }

    private String sha256Hex(
        String value
    ) {

        return HexFormat.of()
            .formatHex(
                sha256Bytes(
                    value
                )
            );
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

    private record Actor(
        UUID userId,
        UUID organizationId,
        UUID studentId
    ) {
    }

    private record StudentActor(
        UUID userId,
        UUID organizationId,
        UUID studentId
    ) {
    }

    private record FoodPassMutationRow(
        UUID id,
        UUID studentId,
        UUID credentialId,
        String status,
        OffsetDateTime expiresAt
    ) {
    }

    private record FoodPassRow(
        UUID id,
        String cardNumber,
        String status,
        OffsetDateTime expiresAt,
        UUID studentId,
        String studentNumber,
        String program,
        String level,
        String groupName,
        String photoUrl
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