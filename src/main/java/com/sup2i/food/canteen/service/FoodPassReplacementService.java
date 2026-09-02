package com.sup2i.food.canteen.service;

import com.sup2i.food.canteen.api.dto.FoodPassReplacementResponse;
import com.sup2i.food.canteen.exception.CanteenErrorCode;
import com.sup2i.food.canteen.exception.CanteenException;
import com.sup2i.food.scan.api.dto.FoodPassResponse;
import com.sup2i.food.scan.api.dto.StudentSummary;
import com.sup2i.food.scan.service.ScanTokenHasher;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FoodPassReplacementService {

    private static final int RESPONSE_STATUS_CREATED =
        201;

    private static final String RESOURCE_TYPE =
        "FOOD_PASS_REPLACEMENT";

    private static final Duration IDEMPOTENCY_RETENTION =
        Duration.ofHours(24);

    private static final SecureRandom SECURE_RANDOM =
        new SecureRandom();

    private final JdbcTemplate jdbcTemplate;

    private final ScanTokenHasher tokenHasher;

    public FoodPassReplacementService(
        JdbcTemplate jdbcTemplate,
        ScanTokenHasher tokenHasher
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.tokenHasher =
            tokenHasher;
    }

    @Transactional
    public FoodPassReplacementResponse replace(
        UUID actorId,
        UUID sourceFoodPassId,
        String rawIdempotencyKey
    ) {
        if (sourceFoodPassId == null) {
            throw validation(
                "foodPassId is required."
            );
        }

        UUID organizationId =
            organizationId(
                actorId
            );

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        String scope =
            "FOOD_PASS_REPLACE:"
                + organizationId;

        String requestHash =
            requestHash(
                actorId,
                sourceFoodPassId
            );

        OffsetDateTime now =
            OffsetDateTime.now();

        lockIdempotency(
            scope,
            idempotencyKey
        );

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

            FoodPassResponse response =
                loadCanonical(
                    organizationId,
                    replay.resourceId()
                );

            return new FoodPassReplacementResponse(
                response,
                null
            );
        }

        SourcePass source =
            lockSource(
                organizationId,
                sourceFoodPassId
            );

        if (!"LOST".equals(source.status())) {
            throw new CanteenException(
                CanteenErrorCode.FOOD_PASS_REVOKED,
                "Only a LOST Food Pass can be replaced."
            );
        }

        boolean expired =
            source.expiresAt() != null
                && !now.isBefore(
                    source.expiresAt()
                );

        if (expired) {
            throw new CanteenException(
                CanteenErrorCode.FOOD_PASS_EXPIRED,
                "Food Pass has expired."
            );
        }

        UUID replacementId =
            UUID.randomUUID();

        UUID replacementCredentialId =
            UUID.randomUUID();

        String rawCredential =
            generateCredential();

        String credentialHash =
            tokenHasher.hash(
                rawCredential
            );

        String cardNumber =
            generateCardNumber();

        int revoked =
            jdbcTemplate.update(
                """
                UPDATE qr_credentials
                SET status = 'REVOKED',
                    revoked_at = COALESCE(
                        revoked_at,
                        ?
                    )
                WHERE id = ?
                  AND credential_type = 'FOOD_PASS'
                """,
                now,
                source.credentialId()
            );

        if (revoked != 1) {
            throw new IllegalStateException(
                "Previous Food Pass credential could not be revoked."
            );
        }

        int credentialInserted =
            jdbcTemplate.update(
                """
                INSERT INTO qr_credentials (
                    id,
                    credential_type,
                    subject_id,
                    token_hash,
                    status,
                    issued_at,
                    expires_at,
                    metadata
                )
                VALUES (
                    ?,
                    'FOOD_PASS',
                    ?,
                    ?,
                    'ACTIVE',
                    ?,
                    ?,
                    '{}'::jsonb
                )
                """,
                replacementCredentialId,
                replacementId,
                credentialHash,
                now,
                source.expiresAt()
            );

        if (credentialInserted != 1) {
            throw new IllegalStateException(
                "Replacement QR credential insert failed."
            );
        }

        int passInserted =
            jdbcTemplate.update(
                """
                INSERT INTO food_passes (
                    id,
                    student_id,
                    credential_id,
                    card_number,
                    status,
                    issued_at,
                    expires_at,
                    issued_by,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    'ACTIVE',
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                replacementId,
                source.studentId(),
                replacementCredentialId,
                cardNumber,
                now,
                source.expiresAt(),
                actorId,
                now,
                now
            );

        if (passInserted != 1) {
            throw new IllegalStateException(
                "Replacement Food Pass insert failed."
            );
        }

        int sourceUpdated =
            jdbcTemplate.update(
                """
                UPDATE food_passes
                SET status = 'REPLACED',
                    replaced_by_id = ?,
                    updated_at = ?
                WHERE id = ?
                  AND status = 'LOST'
                """,
                replacementId,
                now,
                sourceFoodPassId
            );

        if (sourceUpdated != 1) {
            throw new IllegalStateException(
                "Source Food Pass replacement transition failed."
            );
        }

        insertFoodPassEvent(
            sourceFoodPassId,
            "REPLACED",
            actorId,
            "Administrative replacement"
        );

        insertFoodPassEvent(
            replacementId,
            "ISSUED",
            actorId,
            "Replacement Food Pass issued"
        );

        auditReplacement(
            organizationId,
            actorId,
            source,
            replacementId,
            cardNumber
        );

        persistIdempotency(
            scope,
            idempotencyKey,
            actorId,
            requestHash,
            replacementId,
            now.plus(
                IDEMPOTENCY_RETENTION
            )
        );

        FoodPassResponse canonical =
            loadCanonical(
                organizationId,
                replacementId
            );

        return new FoodPassReplacementResponse(
            canonical,
            rawCredential
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

    private SourcePass lockSource(
        UUID organizationId,
        UUID sourceFoodPassId
    ) {
        List<SourcePass> rows =
            jdbcTemplate.query(
                """
                SELECT
                    fp.id,
                    fp.student_id,
                    fp.credential_id,
                    fp.card_number,
                    fp.status,
                    fp.expires_at
                FROM food_passes fp
                JOIN qr_credentials qc
                  ON qc.id = fp.credential_id
                 AND qc.subject_id = fp.id
                 AND qc.credential_type = 'FOOD_PASS'
                JOIN students s
                  ON s.id = fp.student_id
                JOIN users u
                  ON u.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE fp.id = ?
                  AND u.organization_id = ?
                  AND c.organization_id = ?
                FOR UPDATE OF fp, qc
                """,
                (resultSet, rowNumber) ->
                    new SourcePass(
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
                            "card_number"
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "expires_at",
                            OffsetDateTime.class
                        )
                    ),
                sourceFoodPassId,
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
                "Food Pass lookup returned multiple rows."
            );
        }

        return rows.getFirst();
    }

    private FoodPassResponse loadCanonical(
        UUID organizationId,
        UUID foodPassId
    ) {
        List<FoodPassResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    fp.id,
                    fp.card_number,
                    fp.status,
                    fp.expires_at,
                    s.id AS student_id,
                    s.student_number,
                    s.program,
                    s.level,
                    s.group_name,
                    sp.photo_url
                FROM food_passes fp
                JOIN students s
                  ON s.id = fp.student_id
                JOIN users u
                  ON u.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                LEFT JOIN student_photos sp
                  ON sp.student_id = s.id
                 AND sp.is_current = TRUE
                 AND sp.revoked_at IS NULL
                WHERE fp.id = ?
                  AND u.organization_id = ?
                  AND c.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    new FoodPassResponse(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "card_number"
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "expires_at",
                            OffsetDateTime.class
                        ),
                        new StudentSummary(
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
                        )
                    ),
                foodPassId,
                organizationId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Canonical replacement Food Pass could not be resolved."
            );
        }

        return rows.getFirst();
    }

    private void insertFoodPassEvent(
        UUID foodPassId,
        String eventType,
        UUID actorId,
        String reason
    ) {
        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO food_pass_events (
                    id,
                    food_pass_id,
                    event_type,
                    reason,
                    performed_by
                )
                VALUES (
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
                "Food Pass lifecycle event insert failed."
            );
        }
    }

    private void auditReplacement(
        UUID organizationId,
        UUID actorId,
        SourcePass source,
        UUID replacementId,
        String replacementCardNumber
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
                    'FOOD_PASS_REPLACED',
                    'FOOD_PASS',
                    ?,
                    jsonb_build_object(
                        'status',
                        'LOST',
                        'cardNumber',
                        ?
                    ),
                    jsonb_build_object(
                        'status',
                        'REPLACED',
                        'replacementId',
                        ?::text,
                        'replacementCardNumber',
                        ?
                    ),
                    'API',
                    'SUCCESS'
                )
                """,
                UUID.randomUUID(),
                organizationId,
                actorId,
                source.id(),
                source.cardNumber(),
                replacementId,
                replacementCardNumber
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Food Pass replacement audit insert failed."
            );
        }
    }

    private String generateCredential() {
        byte[] bytes =
            new byte[32];

        SECURE_RANDOM.nextBytes(
            bytes
        );

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                bytes
            );
    }

    private String generateCardNumber() {
        String value =
            UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();

        return "FP-"
            + value.substring(
                0,
                24
            );
    }

    private String normalizeIdempotencyKey(
        String raw
    ) {
        if (raw == null) {
            throw validation(
                "Idempotency-Key header is required."
            );
        }

        String value =
            raw.trim();

        if (value.length() < 8) {
            throw validation(
                "Idempotency-Key must contain at least 8 characters."
            );
        }

        if (value.length() > 160) {
            throw validation(
                "Idempotency-Key cannot exceed 160 characters."
            );
        }

        return value;
    }

    private void lockIdempotency(
        String scope,
        String key
    ) {
        byte[] digest =
            sha256Bytes(
                "FOOD_PASS_REPLACE_IDEMPOTENCY"
                    + "\n"
                    + scope
                    + "\n"
                    + key
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

    private Optional<StoredIdempotency> findStored(
        String scope,
        String key
    ) {
        List<StoredIdempotency> rows =
            jdbcTemplate.query(
                """
                SELECT
                    user_id,
                    request_hash,
                    response_status,
                    resource_type,
                    resource_id
                FROM idempotency_records
                WHERE scope = ?
                  AND idempotency_key = ?
                """,
                (resultSet, rowNumber) ->
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
                            "resource_type"
                        ),
                        resultSet.getObject(
                            "resource_id",
                            UUID.class
                        )
                    ),
                scope,
                key
            );

        if (rows.size() > 1) {
            throw new IllegalStateException(
                "Food Pass replacement idempotency lookup returned multiple rows."
            );
        }

        return rows
            .stream()
            .findFirst();
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
            stored.resourceId() != null;

        boolean valid =
            sameUser
                && sameRequest
                && sameStatus
                && sameResourceType
                && hasResource;

        if (!valid) {
            throw new CanteenException(
                CanteenErrorCode.IDEMPOTENCY_CONFLICT,
                "Idempotency-Key was already used for another request."
            );
        }
    }

    private void persistIdempotency(
        String scope,
        String key,
        UUID actorId,
        String requestHash,
        UUID replacementId,
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
                    '{"credentialPersisted":false}'::jsonb,
                    ?,
                    ?,
                    ?
                )
                """,
                UUID.randomUUID(),
                key,
                scope,
                actorId,
                requestHash,
                RESPONSE_STATUS_CREATED,
                RESOURCE_TYPE,
                replacementId,
                expiresAt
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "Food Pass replacement idempotency insert failed."
            );
        }
    }

    private String requestHash(
        UUID actorId,
        UUID sourceFoodPassId
    ) {
        String canonical =
            "FOOD_PASS_REPLACE"
                + "\n"
                + actorId
                + "\n"
                + sourceFoodPassId;

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

    private CanteenException validation(
        String message
    ) {
        return new CanteenException(
            CanteenErrorCode.VALIDATION_ERROR,
            message
        );
    }

    private record SourcePass(
        UUID id,
        UUID studentId,
        UUID credentialId,
        String cardNumber,
        String status,
        OffsetDateTime expiresAt
    ) {
    }

    private record StoredIdempotency(
        UUID userId,
        String requestHash,
        int responseStatus,
        String resourceType,
        UUID resourceId
    ) {
    }
}