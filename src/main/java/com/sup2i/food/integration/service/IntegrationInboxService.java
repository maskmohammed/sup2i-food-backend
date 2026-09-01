package com.sup2i.food.integration.service;

import com.sup2i.food.integration.api.dto.IntegrationInboxEventResponse;
import com.sup2i.food.integration.api.dto.RecordIntegrationInboxEventCommand;
import com.sup2i.food.integration.domain.IntegrationInboxStatus;
import com.sup2i.food.integration.exception.IntegrationConflictException;
import com.sup2i.food.integration.exception.IntegrationNotFoundException;
import com.sup2i.food.integration.exception.IntegrationValidationException;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class IntegrationInboxService {

    private static final int EXTERNAL_EVENT_ID_MAX_LENGTH =
        200;

    private static final int EVENT_TYPE_MAX_LENGTH =
        120;

    private final JdbcTemplate jdbcTemplate;

    public IntegrationInboxService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public IntegrationInboxEventResponse record(
        UUID organizationId,
        UUID eventId,
        RecordIntegrationInboxEventCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            eventId,
            "Inbox event id"
        );

        if (command == null) {

            throw new IntegrationValidationException(
                "Integration inbox event command is required."
            );
        }

        requireId(
            command.connectorId(),
            "Connector id"
        );

        String externalEventId =
            normalizeOptional(
                command.externalEventId(),
                EXTERNAL_EVENT_ID_MAX_LENGTH,
                "External event id"
            );

        String eventType =
            normalizeRequired(
                command.eventType(),
                EVENT_TYPE_MAX_LENGTH,
                "Event type"
            );

        String payloadJson =
            canonicalRequiredJson(
                command.payloadJson(),
                "Inbox payload"
            );

        lockConnector(
            organizationId,
            command.connectorId()
        );

        IntegrationInboxEventResponse existingById =
            findById(
                organizationId,
                eventId
            );

        if (existingById != null) {

            if (
                samePayload(
                    existingById,
                    command.connectorId(),
                    externalEventId,
                    eventType,
                    payloadJson
                )
            ) {

                return replay(
                    existingById
                );
            }

            throw new IntegrationConflictException(
                "Integration inbox event identifier is already used by another payload."
            );
        }

        if (externalEventId != null) {

            IntegrationInboxEventResponse existingExternal =
                findByExternalEventId(
                    organizationId,
                    command.connectorId(),
                    externalEventId
                );

            if (existingExternal != null) {

                if (
                    samePayload(
                        existingExternal,
                        command.connectorId(),
                        externalEventId,
                        eventType,
                        payloadJson
                    )
                ) {

                    return replay(
                        existingExternal
                    );
                }

                throw new IntegrationConflictException(
                    "External event identifier was already received with another payload."
                );
            }
        }

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO integration_inbox_events(
                    id,
                    connector_id,
                    external_event_id,
                    event_type,
                    payload,
                    status,
                    retry_count
                )
                VALUES(
                    ?, ?, ?, ?,
                    CAST(? AS JSONB),
                    'RECEIVED',
                    0
                )
                ON CONFLICT
                DO NOTHING
                """,
                eventId,
                command.connectorId(),
                externalEventId,
                eventType,
                payloadJson
            );

        IntegrationInboxEventResponse stored =
            findById(
                organizationId,
                eventId
            );

        if (inserted == 0) {

            if (
                stored != null
                && samePayload(
                    stored,
                    command.connectorId(),
                    externalEventId,
                    eventType,
                    payloadJson
                )
            ) {

                return replay(
                    stored
                );
            }

            if (externalEventId != null) {

                IntegrationInboxEventResponse externalStored =
                    findByExternalEventId(
                        organizationId,
                        command.connectorId(),
                        externalEventId
                    );

                if (
                    externalStored != null
                    && samePayload(
                        externalStored,
                        command.connectorId(),
                        externalEventId,
                        eventType,
                        payloadJson
                    )
                ) {

                    return replay(
                        externalStored
                    );
                }
            }

            throw new IntegrationConflictException(
                "Integration inbox event conflicts with an existing event."
            );
        }

        if (stored == null) {

            throw new IntegrationConflictException(
                "Integration inbox event could not be resolved after creation."
            );
        }

        return stored;
    }

    @Transactional(readOnly = true)
    public IntegrationInboxEventResponse get(
        UUID organizationId,
        UUID eventId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            eventId,
            "Inbox event id"
        );

        IntegrationInboxEventResponse response =
            findById(
                organizationId,
                eventId
            );

        if (response == null) {

            throw new IntegrationNotFoundException(
                "Integration inbox event does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<IntegrationInboxEventResponse> listByStatus(
        UUID organizationId,
        UUID connectorId,
        IntegrationInboxStatus status
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            connectorId,
            "Connector id"
        );

        if (status == null) {

            throw new IntegrationValidationException(
                "Inbox event status is required."
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                e.id,
                e.connector_id,
                e.external_event_id,
                e.event_type,
                e.payload::text AS payload_json,
                e.status,
                e.received_at,
                e.processed_at,
                e.retry_count,
                e.last_error
            FROM integration_inbox_events e
            JOIN integration_connectors c
              ON c.id = e.connector_id
            WHERE e.connector_id = ?
              AND c.organization_id = ?
              AND e.status = ?
            ORDER BY
                e.received_at,
                e.id
            """,
            (rs, rowNum) ->
                mapRow(
                    rs,
                    false
                ),
            connectorId,
            organizationId,
            status.name()
        );
    }

    private IntegrationInboxEventResponse findById(
        UUID organizationId,
        UUID eventId
    ) {

        List<IntegrationInboxEventResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    e.id,
                    e.connector_id,
                    e.external_event_id,
                    e.event_type,
                    e.payload::text AS payload_json,
                    e.status,
                    e.received_at,
                    e.processed_at,
                    e.retry_count,
                    e.last_error
                FROM integration_inbox_events e
                JOIN integration_connectors c
                  ON c.id = e.connector_id
                WHERE e.id = ?
                  AND c.organization_id = ?
                """,
                (rs, rowNum) ->
                    mapRow(
                        rs,
                        false
                    ),
                eventId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(
            0
        );
    }

    private IntegrationInboxEventResponse findByExternalEventId(
        UUID organizationId,
        UUID connectorId,
        String externalEventId
    ) {

        List<IntegrationInboxEventResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    e.id,
                    e.connector_id,
                    e.external_event_id,
                    e.event_type,
                    e.payload::text AS payload_json,
                    e.status,
                    e.received_at,
                    e.processed_at,
                    e.retry_count,
                    e.last_error
                FROM integration_inbox_events e
                JOIN integration_connectors c
                  ON c.id = e.connector_id
                WHERE e.connector_id = ?
                  AND c.organization_id = ?
                  AND e.external_event_id = ?
                """,
                (rs, rowNum) ->
                    mapRow(
                        rs,
                        false
                    ),
                connectorId,
                organizationId,
                externalEventId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(
            0
        );
    }

    private IntegrationInboxEventResponse mapRow(
        ResultSet rs,
        boolean replayed
    ) throws SQLException {

        return new IntegrationInboxEventResponse(
            rs.getObject(
                "id",
                UUID.class
            ),
            rs.getObject(
                "connector_id",
                UUID.class
            ),
            rs.getString(
                "external_event_id"
            ),
            rs.getString(
                "event_type"
            ),
            rs.getString(
                "payload_json"
            ),
            IntegrationInboxStatus.valueOf(
                rs.getString(
                    "status"
                )
            ),
            rs.getObject(
                "received_at",
                OffsetDateTime.class
            ),
            rs.getObject(
                "processed_at",
                OffsetDateTime.class
            ),
            rs.getInt(
                "retry_count"
            ),
            rs.getString(
                "last_error"
            ),
            replayed
        );
    }

    private IntegrationInboxEventResponse replay(
        IntegrationInboxEventResponse response
    ) {

        return new IntegrationInboxEventResponse(
            response.id(),
            response.connectorId(),
            response.externalEventId(),
            response.eventType(),
            response.payloadJson(),
            response.status(),
            response.receivedAt(),
            response.processedAt(),
            response.retryCount(),
            response.lastError(),
            true
        );
    }

    private boolean samePayload(
        IntegrationInboxEventResponse existing,
        UUID connectorId,
        String externalEventId,
        String eventType,
        String payloadJson
    ) {

        return existing.connectorId()
            .equals(
                connectorId
            )
            && Objects.equals(
                existing.externalEventId(),
                externalEventId
            )
            && existing.eventType()
                .equals(
                    eventType
                )
            && sameJson(
                existing.payloadJson(),
                payloadJson
            );
    }

    private void lockConnector(
        UUID organizationId,
        UUID connectorId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM integration_connectors
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                connectorId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new IntegrationNotFoundException(
                "Integration connector does not exist."
            );
        }
    }

    private String canonicalRequiredJson(
        String value,
        String label
    ) {

        if (
            value == null
            || value.trim().isEmpty()
        ) {

            throw new IntegrationValidationException(
                label + " must contain valid JSON."
            );
        }

        try {

            String canonical =
                jdbcTemplate.queryForObject(
                    """
                    SELECT CAST(? AS JSONB)::text
                    """,
                    String.class,
                    value
                );

            if (canonical == null) {

                throw new IntegrationValidationException(
                    label + " must contain valid JSON."
                );
            }

            return canonical;

        } catch (DataAccessException exception) {

            throw new IntegrationValidationException(
                label + " must contain valid JSON."
            );
        }
    }

    private boolean sameJson(
        String left,
        String right
    ) {

        if (
            left == null
            || right == null
        ) {
            return left == right;
        }

        try {

            Boolean equal =
                jdbcTemplate.queryForObject(
                    """
                    SELECT
                        CAST(? AS JSONB)
                        =
                        CAST(? AS JSONB)
                    """,
                    Boolean.class,
                    left,
                    right
                );

            return Boolean.TRUE.equals(
                equal
            );

        } catch (DataAccessException exception) {

            throw new IntegrationConflictException(
                "Stored inbox JSON is invalid."
            );
        }
    }

    private String normalizeRequired(
        String value,
        int maxLength,
        String label
    ) {

        if (value == null) {

            throw new IntegrationValidationException(
                label + " is required."
            );
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {

            throw new IntegrationValidationException(
                label + " is required."
            );
        }

        if (normalized.length() > maxLength) {

            throw new IntegrationValidationException(
                label + " exceeds maximum length."
            );
        }

        return normalized;
    }

    private String normalizeOptional(
        String value,
        int maxLength,
        String label
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > maxLength) {

            throw new IntegrationValidationException(
                label + " exceeds maximum length."
            );
        }

        return normalized;
    }

    private void requireId(
        UUID id,
        String label
    ) {

        if (id == null) {

            throw new IntegrationValidationException(
                label + " is required."
            );
        }
    }
}