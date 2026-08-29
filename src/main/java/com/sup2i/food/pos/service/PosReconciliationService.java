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
public class PosReconciliationService {

    private static final int
        IDEMPOTENCY_KEY_MIN_LENGTH =
            8;

    private static final int
        IDEMPOTENCY_KEY_MAX_LENGTH =
            160;

    private static final int
        RESPONSE_STATUS =
            200;

    private static final String
        RESOURCE_TYPE =
            "POS_SESSION";

    /*
     * Le MASTER impose un seuil de validation superviseur
     * mais ne fixe aucune valeur numérique.
     *
     * MVP conservateur :
     * tout écart non nul nécessite supervision.
     */
    private static final BigDecimal
        SUPERVISOR_DIFFERENCE_THRESHOLD =
            BigDecimal.ZERO.setScale(2);

    private final JdbcTemplate jdbcTemplate;

    private final JsonMapper jsonMapper;

    public PosReconciliationService(
        JdbcTemplate jdbcTemplate,
        JsonMapper jsonMapper
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.jsonMapper =
            jsonMapper;
    }

    @Transactional
    public PosSessionResponse close(
        UUID actorId,
        UUID sessionId,
        BigDecimal countedCash,
        String comment,
        String rawIdempotencyKey
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        BigDecimal normalizedCounted =
            money(
                countedCash,
                "countedCash"
            );

        String normalizedComment =
            normalizeComment(
                comment,
                false
            );

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        String scope =
            "POS_SESSION_CLOSE:"
                + organizationId;

        String requestHash =
            sha256(
                "CLOSE|"
                    + sessionId
                    + "|"
                    + normalizedCounted.toPlainString()
                    + "|"
                    + identityText(
                        normalizedComment
                    )
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
                actorId,
                requestHash,
                sessionId
            );
        }

        SessionContext session =
            lockOwnedOpenSession(
                sessionId,
                actorId,
                organizationId
            );

        Reconciliation reconciliation =
            reconcile(
                session,
                normalizedCounted
            );

        if (
            reconciliation.supervisorRequired()
            && normalizedComment == null
        ) {

            throw new PosException(
                PosErrorCode
                    .CASH_DIFFERENCE_REASON_REQUIRED,
                "Cash difference requires a closing comment."
            );
        }

        synchronizeTenderTotals(
            session.id(),
            reconciliation,
            normalizedCounted
        );

        int updated =
            jdbcTemplate.update(
                """
                UPDATE pos_sessions
                SET expected_cash = ?,
                    counted_cash = ?,
                    difference = ?,
                    close_reason = ?,
                    supervisor_required = ?,
                    closed_at = CURRENT_TIMESTAMP,
                    status = 'CLOSED'
                WHERE id = ?
                  AND status = 'OPEN'
                """,
                reconciliation.expectedCash(),
                normalizedCounted,
                reconciliation.difference(),
                normalizedComment,
                reconciliation.supervisorRequired(),
                session.id()
            );

        if (updated != 1) {

            throw new PosException(
                PosErrorCode.POS_SESSION_NOT_OPEN,
                "POS session is no longer OPEN."
            );
        }

        PosSessionResponse response =
            response(
                session.id(),
                organizationId
            );

        persistIdempotency(
            scope,
            idempotencyKey,
            actorId,
            requestHash,
            response
        );

        return response;
    }

    @Transactional
    public PosSessionResponse forceClose(
        UUID actorId,
        UUID sessionId,
        BigDecimal countedCash,
        String comment,
        String rawIdempotencyKey
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        String normalizedComment =
            normalizeComment(
                comment,
                true
            );

        BigDecimal normalizedCounted =
            countedCash == null
                ? null
                : money(
                    countedCash,
                    "countedCash"
                );

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        String scope =
            "POS_SESSION_FORCE_CLOSE:"
                + organizationId;

        String countIdentity =
            normalizedCounted == null
                ? "<null>"
                : normalizedCounted
                    .toPlainString();

        String requestHash =
            sha256(
                "FORCE_CLOSE|"
                    + sessionId
                    + "|"
                    + countIdentity
                    + "|"
                    + normalizedComment
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
                actorId,
                requestHash,
                sessionId
            );
        }

        SessionContext session =
            lockOrganizationOpenSession(
                sessionId,
                organizationId
            );

        Reconciliation reconciliation =
            reconcile(
                session,
                normalizedCounted
            );

        synchronizeTenderTotals(
            session.id(),
            reconciliation,
            normalizedCounted
        );

        int updated =
            jdbcTemplate.update(
                """
                UPDATE pos_sessions
                SET expected_cash = ?,
                    counted_cash = ?,
                    difference = ?,
                    close_reason = ?,
                    supervisor_required = FALSE,
                    validated_by = ?,
                    validated_at = CURRENT_TIMESTAMP,
                    forced_closed_by = ?,
                    closed_at = CURRENT_TIMESTAMP,
                    status = 'FORCED_CLOSED'
                WHERE id = ?
                  AND status = 'OPEN'
                """,
                reconciliation.expectedCash(),
                normalizedCounted,
                reconciliation.difference(),
                normalizedComment,
                actorId,
                actorId,
                session.id()
            );

        if (updated != 1) {

            throw new PosException(
                PosErrorCode.POS_SESSION_NOT_OPEN,
                "POS session is no longer OPEN."
            );
        }

        PosSessionResponse response =
            response(
                session.id(),
                organizationId
            );

        persistIdempotency(
            scope,
            idempotencyKey,
            actorId,
            requestHash,
            response
        );

        return response;
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

    private SessionContext lockOwnedOpenSession(
        UUID sessionId,
        UUID actorId,
        UUID organizationId
    ) {

        if (sessionId == null) {

            throw new PosValidationException(
                "sessionId is required."
            );
        }

        List<SessionContext> sessions =
            jdbcTemplate.query(
                """
                SELECT
                    ps.id,
                    ps.terminal_id,
                    ps.cashier_id,
                    ps.opening_cash
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
                this::mapContext,
                sessionId,
                actorId,
                organizationId
            );

        if (sessions.isEmpty()) {

            throw new PosException(
                PosErrorCode.POS_SESSION_NOT_OPEN,
                "POS session does not exist, is not OPEN, or is not owned by this cashier."
            );
        }

        return sessions.get(0);
    }

    private SessionContext lockOrganizationOpenSession(
        UUID sessionId,
        UUID organizationId
    ) {

        if (sessionId == null) {

            throw new PosValidationException(
                "sessionId is required."
            );
        }

        List<SessionContext> sessions =
            jdbcTemplate.query(
                """
                SELECT
                    ps.id,
                    ps.terminal_id,
                    ps.cashier_id,
                    ps.opening_cash
                FROM pos_sessions ps
                JOIN pos_terminals pt
                  ON pt.id = ps.terminal_id
                JOIN locations l
                  ON l.id = pt.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE ps.id = ?
                  AND c.organization_id = ?
                  AND ps.status = 'OPEN'
                FOR UPDATE OF ps
                """,
                this::mapContext,
                sessionId,
                organizationId
            );

        if (sessions.isEmpty()) {

            throw new PosException(
                PosErrorCode.POS_SESSION_NOT_OPEN,
                "POS session does not exist or is not OPEN in the organization."
            );
        }

        return sessions.get(0);
    }

    private SessionContext mapContext(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {

        return new SessionContext(
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
            resultSet.getBigDecimal(
                "opening_cash"
            )
        );
    }

    private Reconciliation reconcile(
        SessionContext session,
        BigDecimal countedCash
    ) {

        BigDecimal netCash =
            netTender(
                session.id(),
                "CASH"
            );

        BigDecimal netCard =
            netTender(
                session.id(),
                "CARD_TPE"
            );

        BigDecimal manualCashDelta =
            manualCashDelta(
                session.id()
            );

        BigDecimal expectedCash =
            session
                .openingCash()
                .add(
                    netCash
                )
                .add(
                    manualCashDelta
                )
                .setScale(
                    2,
                    RoundingMode.UNNECESSARY
                );

        if (
            expectedCash.signum()
                < 0
        ) {

            throw new IllegalStateException(
                "Calculated expected cash cannot be negative."
            );
        }

        BigDecimal difference =
            countedCash == null
                ? null
                : countedCash
                    .subtract(
                        expectedCash
                    )
                    .setScale(
                        2,
                        RoundingMode.UNNECESSARY
                    );

        boolean supervisorRequired =
            difference != null;

        if (supervisorRequired) {

            supervisorRequired =
                difference
                    .abs()
                    .compareTo(
                        SUPERVISOR_DIFFERENCE_THRESHOLD
                    ) > 0;
        }

        return new Reconciliation(
            expectedCash,
            netCash,
            netCard,
            difference,
            supervisorRequired
        );
    }

    private BigDecimal netTender(
        UUID sessionId,
        String method
    ) {

        BigDecimal amount =
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    SUM(
                        p.amount
                        - COALESCE(
                            (
                                SELECT SUM(r.amount)
                                FROM refunds r
                                WHERE r.payment_id = p.id
                                  AND r.status = 'COMPLETED'
                            ),
                            0
                        )
                    ),
                    0
                )
                FROM payments p
                WHERE p.pos_session_id = ?
                  AND p.method = ?
                  AND p.status IN (
                      'COMPLETED',
                      'PARTIALLY_REFUNDED',
                      'REFUNDED'
                  )
                """,
                BigDecimal.class,
                sessionId,
                method
            );

        if (amount == null) {
            return BigDecimal.ZERO
                .setScale(2);
        }

        return amount.setScale(
            2,
            RoundingMode.UNNECESSARY
        );
    }

    private BigDecimal manualCashDelta(
        UUID sessionId
    ) {

        BigDecimal amount =
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    SUM(
                        CASE
                            WHEN type = 'CASH_IN'
                                THEN amount
                            WHEN type = 'CASH_OUT'
                                THEN -amount
                            ELSE 0
                        END
                    ),
                    0
                )
                FROM cash_movements
                WHERE pos_session_id = ?
                """,
                BigDecimal.class,
                sessionId
            );

        if (amount == null) {
            return BigDecimal.ZERO
                .setScale(2);
        }

        return amount.setScale(
            2,
            RoundingMode.UNNECESSARY
        );
    }

    private void synchronizeTenderTotals(
        UUID sessionId,
        Reconciliation reconciliation,
        BigDecimal countedCash
    ) {

        upsertTender(
            sessionId,
            "CASH",
            reconciliation.netCash(),
            countedCash,
            reconciliation.difference()
        );

        upsertTender(
            sessionId,
            "CARD_TPE",
            reconciliation.cardTotal(),
            reconciliation.cardTotal(),
            BigDecimal.ZERO.setScale(2)
        );
    }

    private void upsertTender(
        UUID sessionId,
        String method,
        BigDecimal theoretical,
        BigDecimal countedOrSettled,
        BigDecimal difference
    ) {

        int changed =
            jdbcTemplate.update(
                """
                INSERT INTO pos_session_tender_totals (
                    pos_session_id,
                    payment_method,
                    theoretical_amount,
                    counted_or_settled_amount,
                    difference
                )
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (
                    pos_session_id,
                    payment_method
                )
                DO UPDATE SET
                    theoretical_amount =
                        EXCLUDED.theoretical_amount,
                    counted_or_settled_amount =
                        EXCLUDED.counted_or_settled_amount,
                    difference =
                        EXCLUDED.difference
                """,
                sessionId,
                method,
                theoretical,
                countedOrSettled,
                difference
            );

        if (changed != 1) {

            throw new IllegalStateException(
                "POS tender reconciliation did not affect exactly one row."
            );
        }
    }

    private PosSessionResponse response(
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
                    ) AS card_total,
                    ps.difference
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
                this::mapResponse,
                sessionId,
                organizationId
            );

        if (sessions.size() != 1) {

            throw new PosException(
                PosErrorCode.RESOURCE_NOT_FOUND,
                "POS session does not exist in the organization."
            );
        }

        return sessions.get(0);
    }

    private PosSessionResponse mapResponse(
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
            ),
            resultSet.getBigDecimal(
                "difference"
            )
        );
    }

    private BigDecimal money(
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
                field + " cannot be negative."
            );
        }

        try {

            BigDecimal normalized =
                value.setScale(
                    2,
                    RoundingMode.UNNECESSARY
                );

            if (normalized.precision() > 12) {

                throw new PosValidationException(
                    field + " exceeds NUMERIC(12,2)."
                );
            }

            return normalized;

        } catch (
            ArithmeticException exception
        ) {

            throw new PosValidationException(
                field + " supports at most two decimal places."
            );
        }
    }

    private String normalizeComment(
        String value,
        boolean required
    ) {

        if (value == null) {

            if (required) {

                throw new PosValidationException(
                    "comment is required."
                );
            }

            return null;
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {

            if (required) {

                throw new PosValidationException(
                    "comment is required."
                );
            }

            return null;
        }

        if (normalized.length() > 2000) {

            throw new PosValidationException(
                "comment cannot exceed 2000 characters."
            );
        }

        return normalized;
    }

    private String identityText(
        String value
    ) {

        return value == null
            ? "<null>"
            : value;
    }

    private String normalizeIdempotencyKey(
        String value
    ) {

        if (value == null) {

            throw new PosValidationException(
                "Idempotency-Key is required."
            );
        }

        String normalized =
            value.trim();

        if (
            normalized.length()
                < IDEMPOTENCY_KEY_MIN_LENGTH
        ) {

            throw new PosValidationException(
                "Idempotency-Key must contain at least 8 characters."
            );
        }

        if (
            normalized.length()
                > IDEMPOTENCY_KEY_MAX_LENGTH
        ) {

            throw new PosValidationException(
                "Idempotency-Key cannot exceed 160 characters."
            );
        }

        return normalized;
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
            throw idempotencyConflict();
        }

        return Optional.of(
            records.get(0)
        );
    }

    private PosSessionResponse replay(
        StoredIdempotency stored,
        UUID actorId,
        String requestHash,
        UUID sessionId
    ) {

        boolean valid =
            actorId.equals(
                stored.userId()
            );

        if (valid) {

            valid =
                requestHash.equals(
                    stored.requestHash()
                );
        }

        if (valid) {

            valid =
                stored.responseStatus()
                    == RESPONSE_STATUS;
        }

        if (valid) {

            valid =
                RESOURCE_TYPE.equals(
                    stored.resourceType()
                );
        }

        if (valid) {

            valid =
                sessionId.equals(
                    stored.resourceId()
                );
        }

        if (valid) {

            valid =
                stored.responseBody()
                    != null;
        }

        if (!valid) {
            throw idempotencyConflict();
        }

        PosSessionResponse response =
            deserialize(
                stored.responseBody()
            );

        if (
            !sessionId.equals(
                response.id()
            )
        ) {
            throw idempotencyConflict();
        }

        return response;
    }

    private void persistIdempotency(
        String scope,
        String idempotencyKey,
        UUID actorId,
        String requestHash,
        PosSessionResponse response
    ) {

        String body =
            serialize(
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
                    ?, ?, ?, ?, ?,
                    CAST(? AS jsonb),
                    ?, ?, ?
                )
                """,
                idempotencyKey,
                scope,
                actorId,
                requestHash,
                RESPONSE_STATUS,
                body,
                RESOURCE_TYPE,
                response.id(),
                OffsetDateTime
                    .now()
                    .plusHours(24)
            );

        if (inserted != 1) {

            throw new IllegalStateException(
                "POS reconciliation idempotency insert did not affect exactly one row."
            );
        }
    }

    private String serialize(
        PosSessionResponse response
    ) {

        try {

            return jsonMapper
                .writeValueAsString(
                    response
                );

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Unable to serialize POS reconciliation response.",
                exception
            );
        }
    }

    private PosSessionResponse deserialize(
        String body
    ) {

        try {

            return jsonMapper.readValue(
                body,
                PosSessionResponse.class
            );

        } catch (Exception exception) {

            throw idempotencyConflict();
        }
    }

    private PosException idempotencyConflict() {

        return new PosException(
            PosErrorCode.IDEMPOTENCY_CONFLICT,
            "Idempotency-Key is already used with a different POS reconciliation request."
        );
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

    private record SessionContext(
        UUID id,
        UUID terminalId,
        UUID cashierId,
        BigDecimal openingCash
    ) {
    }

    private record Reconciliation(
        BigDecimal expectedCash,
        BigDecimal netCash,
        BigDecimal cardTotal,
        BigDecimal difference,
        boolean supervisorRequired
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