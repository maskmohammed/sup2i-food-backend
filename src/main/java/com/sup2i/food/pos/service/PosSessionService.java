package com.sup2i.food.pos.service;

import com.sup2i.food.pos.api.dto.PosSessionResponse;
import com.sup2i.food.pos.domain.PosSessionStatus;
import com.sup2i.food.pos.exception.PosErrorCode;
import com.sup2i.food.pos.exception.PosException;
import com.sup2i.food.pos.exception.PosValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PosSessionService {

    private static final int IDEMPOTENCY_KEY_MIN_LENGTH =
        8;

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH =
        160;

    private static final int OPEN_RESPONSE_STATUS =
        201;

    private static final int CLOSE_RESPONSE_STATUS =
        200;

    private static final String RESOURCE_TYPE =
        "POS_SESSION";

    private final JdbcTemplate jdbcTemplate;

    private final JsonMapper jsonMapper;

    public PosSessionService(
        JdbcTemplate jdbcTemplate,
        JsonMapper jsonMapper
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.jsonMapper =
            jsonMapper;
    }

    @Transactional
    public PosSessionResponse open(
        UUID actorUserId,
        UUID terminalId,
        BigDecimal openingCash,
        String rawIdempotencyKey
    ) {

        UUID organizationId =
            organizationId(
                actorUserId
            );

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        BigDecimal normalizedOpeningCash =
            normalizeMoney(
                openingCash,
                "openingCash"
            );

        String scope =
            "POS_SESSION_OPEN:"
                + organizationId;

        String requestIdentity =
            "OPEN|"
                + terminalId
                + "|"
                + normalizedOpeningCash.toPlainString();

        String requestHash =
            sha256(
                requestIdentity
            );

        lockIdempotencyKey(
            scope,
            idempotencyKey
        );

        deleteExpiredIdempotency(
            scope,
            idempotencyKey
        );

        Optional<StoredIdempotency> stored =
            findIdempotency(
                scope,
                idempotencyKey
            );

        if (stored.isPresent()) {

            return replay(
                stored.get(),
                actorUserId,
                requestHash,
                OPEN_RESPONSE_STATUS,
                null
            );
        }

        TerminalRow terminal =
            lockTerminal(
                terminalId,
                organizationId
            );

        if (
            openSessionExists(
                terminal.id()
            )
        ) {
            throw new PosException(
                PosErrorCode.POS_SESSION_ALREADY_OPEN,
                "An OPEN POS session already exists for this terminal."
            );
        }

        UUID sessionId =
            UUID.randomUUID();

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO pos_sessions (
                    id,
                    terminal_id,
                    cashier_id,
                    opening_cash,
                    status
                )
                VALUES (?, ?, ?, ?, 'OPEN')
                """,
                sessionId,
                terminal.id(),
                actorUserId,
                normalizedOpeningCash
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "POS session insert did not affect exactly one row."
            );
        }

        PosSessionResponse response =
            sessionResponse(
                sessionId,
                organizationId
            );

        persistIdempotency(
            scope,
            idempotencyKey,
            actorUserId,
            requestHash,
            OPEN_RESPONSE_STATUS,
            response
        );

        return response;
    }

    @Transactional
    public PosSessionResponse close(
        UUID actorUserId,
        UUID sessionId,
        BigDecimal countedCash,
        String comment,
        String rawIdempotencyKey
    ) {

        UUID organizationId =
            organizationId(
                actorUserId
            );

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        BigDecimal normalizedCountedCash =
            normalizeMoney(
                countedCash,
                "countedCash"
            );

        String normalizedComment =
            normalizeComment(
                comment
            );

        String scope =
            "POS_SESSION_CLOSE:"
                + organizationId;

        String commentIdentity =
            normalizedComment == null
                ? "<null>"
                : normalizedComment;

        String requestIdentity =
            "CLOSE|"
                + sessionId
                + "|"
                + normalizedCountedCash.toPlainString()
                + "|"
                + commentIdentity;

        String requestHash =
            sha256(
                requestIdentity
            );

        lockIdempotencyKey(
            scope,
            idempotencyKey
        );

        deleteExpiredIdempotency(
            scope,
            idempotencyKey
        );

        Optional<StoredIdempotency> stored =
            findIdempotency(
                scope,
                idempotencyKey
            );

        if (stored.isPresent()) {

            return replay(
                stored.get(),
                actorUserId,
                requestHash,
                CLOSE_RESPONSE_STATUS,
                sessionId
            );
        }

        lockOwnedOpenSession(
            sessionId,
            actorUserId,
            organizationId
        );

        int updated =
            jdbcTemplate.update(
                """
                UPDATE pos_sessions
                SET counted_cash = ?,
                    close_reason = ?,
                    closed_at = CURRENT_TIMESTAMP,
                    status = 'CLOSED'
                WHERE id = ?
                  AND status = 'OPEN'
                """,
                normalizedCountedCash,
                normalizedComment,
                sessionId
            );

        if (updated != 1) {
            throw new PosException(
                PosErrorCode.POS_SESSION_NOT_OPEN,
                "POS session is not OPEN."
            );
        }

        PosSessionResponse response =
            sessionResponse(
                sessionId,
                organizationId
            );

        persistIdempotency(
            scope,
            idempotencyKey,
            actorUserId,
            requestHash,
            CLOSE_RESPONSE_STATUS,
            response
        );

        return response;
    }

    private UUID organizationId(
        UUID actorUserId
    ) {

        if (actorUserId == null) {
            throw new BadCredentialsException(
                "Authenticated user is missing."
            );
        }

        List<UUID> organizations =
            jdbcTemplate.query(
                """
                SELECT organization_id
                FROM users
                WHERE id = ?
                  AND status = 'ACTIVE'
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorUserId
            );

        if (organizations.isEmpty()) {
            throw new BadCredentialsException(
                "Authenticated user does not exist or is not active."
            );
        }

        return organizations.get(0);
    }

    private TerminalRow lockTerminal(
        UUID terminalId,
        UUID organizationId
    ) {

        if (terminalId == null) {
            throw new PosValidationException(
                "terminalId is required."
            );
        }

        List<TerminalRow> terminals =
            jdbcTemplate.query(
                """
                SELECT
                    pt.id
                FROM pos_terminals pt
                JOIN locations l
                  ON l.id = pt.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE pt.id = ?
                  AND c.organization_id = ?
                  AND pt.is_active = TRUE
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                  AND pt.terminal_type = 'POS'
                FOR UPDATE OF pt
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new TerminalRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        )
                    ),
                terminalId,
                organizationId
            );

        if (terminals.isEmpty()) {
            throw new PosException(
                PosErrorCode.RESOURCE_NOT_FOUND,
                "POS terminal does not exist, is inactive, or is outside the organization."
            );
        }

        return terminals.get(0);
    }

    private boolean openSessionExists(
        UUID terminalId
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pos_sessions
                WHERE terminal_id = ?
                  AND status = 'OPEN'
                """,
                Long.class,
                terminalId
            );

        return count != null
            && count > 0L;
    }

    private void lockOwnedOpenSession(
        UUID sessionId,
        UUID actorUserId,
        UUID organizationId
    ) {

        if (sessionId == null) {
            throw new PosValidationException(
                "sessionId is required."
            );
        }

        List<UUID> sessions =
            jdbcTemplate.query(
                """
                SELECT ps.id
                FROM pos_sessions ps
                JOIN pos_terminals pt
                  ON pt.id = ps.terminal_id
                JOIN locations l
                  ON l.id = pt.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE ps.id = ?
                  AND ps.cashier_id = ?
                  AND c.organization_id = ?
                  AND ps.status = 'OPEN'
                FOR UPDATE OF ps
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                sessionId,
                actorUserId,
                organizationId
            );

        if (sessions.isEmpty()) {
            throw new PosException(
                PosErrorCode.POS_SESSION_NOT_OPEN,
                "POS session does not exist, is not OPEN, or is not owned by this cashier."
            );
        }
    }

    private PosSessionResponse sessionResponse(
        UUID sessionId,
        UUID organizationId
    ) {

        List<PosSessionResponse> sessions =
            jdbcTemplate.query(
                """
                SELECT
                    ps.id,
                    ps.terminal_id,
                    ps.cashier_id,
                    ps.status,
                    ps.opening_cash,
                    ps.expected_cash,
                    ps.counted_cash,
                    (
                    SELECT pstt.theoretical_amount
                    FROM pos_session_tender_totals pstt
                    WHERE pstt.pos_session_id = ps.id
                      AND pstt.payment_method = 'CARD_TPE'
                ) AS card_total
                FROM pos_sessions ps
                JOIN pos_terminals pt
                  ON pt.id = ps.terminal_id
                JOIN locations l
                  ON l.id = pt.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE ps.id = ?
                  AND c.organization_id = ?
                """,
                this::mapSession,
                sessionId,
                organizationId
            );

        if (sessions.isEmpty()) {
            throw new PosException(
                PosErrorCode.RESOURCE_NOT_FOUND,
                "POS session does not exist in the organization."
            );
        }

        return sessions.get(0);
    }

    private PosSessionResponse mapSession(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {

        return new PosSessionResponse(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "terminal_id",
                UUID.class
            ),
            resultSet.getObject(
                "cashier_id",
                UUID.class
            ),
            PosSessionStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            resultSet.getBigDecimal(
                "opening_cash"
            ),
            resultSet.getBigDecimal(
                "expected_cash"
            ),
            resultSet.getBigDecimal(
                "counted_cash"
            ),
            resultSet.getBigDecimal(
                "card_total"
            )
        );
    }

    private BigDecimal normalizeMoney(
        BigDecimal value,
        String field
    ) {

        if (value == null) {
            throw new PosValidationException(
                field + " is required."
            );
        }

        if (value.signum() < 0) {
            throw new PosValidationException(
                field + " must be greater than or equal to zero."
            );
        }

        BigDecimal normalized;

        try {

            normalized =
                value.setScale(
                    2,
                    RoundingMode.UNNECESSARY
                );

        } catch (
            ArithmeticException exception
        ) {

            throw new PosValidationException(
                field + " supports at most two decimal places."
            );
        }

        if (normalized.precision() > 12) {
            throw new PosValidationException(
                field + " exceeds NUMERIC(12,2)."
            );
        }

        return normalized;
    }

    private String normalizeComment(
        String comment
    ) {

        if (comment == null) {
            return null;
        }

        String normalized =
            comment.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }

    private String normalizeIdempotencyKey(
        String rawIdempotencyKey
    ) {

        if (rawIdempotencyKey == null) {
            throw new PosValidationException(
                "Idempotency-Key is required."
            );
        }

        String idempotencyKey =
            rawIdempotencyKey.trim();

        if (
            idempotencyKey.length()
                < IDEMPOTENCY_KEY_MIN_LENGTH
        ) {
            throw new PosValidationException(
                "Idempotency-Key must contain at least 8 characters."
            );
        }

        if (
            idempotencyKey.length()
                > IDEMPOTENCY_KEY_MAX_LENGTH
        ) {
            throw new PosValidationException(
                "Idempotency-Key cannot exceed 160 characters."
            );
        }

        return idempotencyKey;
    }

    private void lockIdempotencyKey(
        String scope,
        String idempotencyKey
    ) {

        UUID lockUuid =
            UUID.nameUUIDFromBytes(
                (
                    "POS:"
                    + scope
                    + ":"
                    + idempotencyKey
                ).getBytes(
                    StandardCharsets.UTF_8
                )
            );

        long lockKey =
            lockUuid.getMostSignificantBits()
                ^ lockUuid.getLeastSignificantBits();

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(?)",
            statement ->
                statement.setLong(
                    1,
                    lockKey
                ),
            (ResultSetExtractor<Void>)
                resultSet -> null
        );
    }

    private void deleteExpiredIdempotency(
        String scope,
        String idempotencyKey
    ) {

        jdbcTemplate.update(
            """
            DELETE FROM idempotency_records
            WHERE scope = ?
              AND idempotency_key = ?
              AND expires_at <= CURRENT_TIMESTAMP
            """,
            scope,
            idempotencyKey
        );
    }

    private Optional<StoredIdempotency>
        findIdempotency(
            String scope,
            String idempotencyKey
        ) {

        List<StoredIdempotency> records =
            jdbcTemplate.query(
                """
                SELECT
                    user_id,
                    request_hash,
                    response_status,
                    response_body::text AS response_body,
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

        if (records.isEmpty()) {
            return Optional.empty();
        }

        if (records.size() != 1) {
            throw new PosException(
                PosErrorCode.IDEMPOTENCY_CONFLICT,
                "Multiple idempotency records exist for the same POS key."
            );
        }

        return Optional.of(
            records.get(0)
        );
    }

    private PosSessionResponse replay(
        StoredIdempotency stored,
        UUID actorUserId,
        String requestHash,
        int expectedStatus,
        UUID expectedResourceId
    ) {

        boolean sameUser =
            actorUserId.equals(
                stored.userId()
            );

        boolean samePayload =
            requestHash.equals(
                stored.requestHash()
            );

        boolean sameStatus =
            stored.responseStatus()
                == expectedStatus;

        boolean sameResourceType =
            RESOURCE_TYPE.equals(
                stored.resourceType()
            );

        boolean hasResourceId =
            stored.resourceId()
                != null;

        boolean resourceMatches =
            expectedResourceId == null
                || expectedResourceId.equals(
                    stored.resourceId()
                );

        boolean hasResponse =
            stored.responseBody()
                != null;

        boolean validReplay =
            sameUser
                && samePayload
                && sameStatus
                && sameResourceType
                && hasResourceId
                && resourceMatches
                && hasResponse;

        if (!validReplay) {
            throw new PosException(
                PosErrorCode.IDEMPOTENCY_CONFLICT,
                "Idempotency-Key is already used with a different POS request."
            );
        }

        PosSessionResponse response =
            deserializeResponse(
                stored.responseBody()
            );

        if (
            !stored
                .resourceId()
                .equals(
                    response.id()
                )
        ) {
            throw new PosException(
                PosErrorCode.IDEMPOTENCY_CONFLICT,
                "Stored POS idempotency response does not match its resource."
            );
        }

        return response;
    }

    private void persistIdempotency(
        String scope,
        String idempotencyKey,
        UUID actorUserId,
        String requestHash,
        int responseStatus,
        PosSessionResponse response
    ) {

        String responseBody =
            serializeResponse(
                response
            );

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO idempotency_records (
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
                    CAST(? AS jsonb),
                    ?,
                    ?,
                    ?
                )
                """,
                idempotencyKey,
                scope,
                actorUserId,
                requestHash,
                responseStatus,
                responseBody,
                RESOURCE_TYPE,
                response.id(),
                OffsetDateTime
                    .now()
                    .plusHours(
                        24
                    )
            );

        if (inserted != 1) {
            throw new IllegalStateException(
                "POS idempotency insert did not affect exactly one row."
            );
        }
    }

    private String serializeResponse(
        PosSessionResponse response
    ) {

        try {

            return jsonMapper.writeValueAsString(
                response
            );

        } catch (
            Exception exception
        ) {

            throw new IllegalStateException(
                "Unable to serialize POS idempotency response.",
                exception
            );
        }
    }

    private PosSessionResponse deserializeResponse(
        String responseBody
    ) {

        try {

            return jsonMapper.readValue(
                responseBody,
                PosSessionResponse.class
            );

        } catch (
            Exception exception
        ) {

            throw new PosException(
                PosErrorCode.IDEMPOTENCY_CONFLICT,
                "Stored POS idempotency response is invalid."
            );
        }
    }

    private String sha256(
        String value
    ) {

        try {

            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            byte[] bytes =
                digest.digest(
                    value.getBytes(
                        StandardCharsets.UTF_8
                    )
                );

            StringBuilder result =
                new StringBuilder(
                    64
                );

            for (byte item : bytes) {

                String pair =
                    Integer.toHexString(
                        item & 0xff
                    );

                if (pair.length() == 1) {
                    result.append('0');
                }

                result.append(
                    pair
                );
            }

            return result.toString();

        } catch (
            NoSuchAlgorithmException exception
        ) {

            throw new IllegalStateException(
                "SHA-256 is unavailable.",
                exception
            );
        }
    }

    private record TerminalRow(
        UUID id
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