package com.sup2i.food.integration.service;

import com.sup2i.food.integration.api.dto.CreateIntegrationSyncRunCommand;
import com.sup2i.food.integration.api.dto.IntegrationSyncItemResponse;
import com.sup2i.food.integration.api.dto.IntegrationSyncRunResponse;
import com.sup2i.food.integration.api.dto.RecordIntegrationSyncItemCommand;
import com.sup2i.food.integration.domain.IntegrationSyncItemStatus;
import com.sup2i.food.integration.domain.IntegrationSyncRunStatus;
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
public class IntegrationSyncService {

    private static final int SYNC_TYPE_MAX_LENGTH =
        50;

    private static final int ENTITY_TYPE_MAX_LENGTH =
        80;

    private static final int EXTERNAL_ID_MAX_LENGTH =
        255;

    private static final int ACTION_MAX_LENGTH =
        30;

    private static final int ERROR_CODE_MAX_LENGTH =
        100;

    private final JdbcTemplate jdbcTemplate;

    public IntegrationSyncService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public IntegrationSyncRunResponse createRun(
        UUID organizationId,
        UUID syncRunId,
        CreateIntegrationSyncRunCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            syncRunId,
            "Sync run id"
        );

        if (command == null) {

            throw new IntegrationValidationException(
                "Integration sync run command is required."
            );
        }

        requireId(
            command.connectorId(),
            "Connector id"
        );

        String syncType =
            normalizeRequired(
                command.syncType(),
                SYNC_TYPE_MAX_LENGTH,
                "Sync type"
            );

        lockConnector(
            organizationId,
            command.connectorId()
        );

        lockOptionalUser(
            organizationId,
            command.initiatedBy()
        );

        IntegrationSyncRunResponse existing =
            findRunById(
                organizationId,
                syncRunId
            );

        if (existing != null) {

            if (
                sameRunRequest(
                    existing,
                    command.connectorId(),
                    syncType,
                    command.initiatedBy()
                )
            ) {

                return replayRun(
                    existing
                );
            }

            throw new IntegrationConflictException(
                "Integration sync run identifier is already used by another request."
            );
        }

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO integration_sync_runs(
                    id,
                    connector_id,
                    sync_type,
                    status,
                    processed_count,
                    success_count,
                    failure_count,
                    initiated_by
                )
                VALUES(
                    ?, ?, ?,
                    'RUNNING',
                    0, 0, 0,
                    ?
                )
                ON CONFLICT (id)
                DO NOTHING
                """,
                syncRunId,
                command.connectorId(),
                syncType,
                command.initiatedBy()
            );

        IntegrationSyncRunResponse stored =
            findRunById(
                organizationId,
                syncRunId
            );

        if (inserted == 0) {

            if (
                stored != null
                && sameRunRequest(
                    stored,
                    command.connectorId(),
                    syncType,
                    command.initiatedBy()
                )
            ) {

                return replayRun(
                    stored
                );
            }

            throw new IntegrationConflictException(
                "Integration sync run identifier conflicts with another request."
            );
        }

        if (stored == null) {

            throw new IntegrationConflictException(
                "Integration sync run could not be resolved after creation."
            );
        }

        return stored;
    }

    @Transactional
    public IntegrationSyncItemResponse recordItem(
        UUID organizationId,
        UUID itemId,
        RecordIntegrationSyncItemCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            itemId,
            "Sync item id"
        );

        if (command == null) {

            throw new IntegrationValidationException(
                "Integration sync item command is required."
            );
        }

        requireId(
            command.syncRunId(),
            "Sync run id"
        );

        if (command.status() == null) {

            throw new IntegrationValidationException(
                "Sync item status is required."
            );
        }

        String entityType =
            normalizeOptional(
                command.entityType(),
                ENTITY_TYPE_MAX_LENGTH,
                "Sync item entity type"
            );

        String externalId =
            normalizeOptional(
                command.externalId(),
                EXTERNAL_ID_MAX_LENGTH,
                "Sync item external id"
            );

        String action =
            normalizeOptional(
                command.action(),
                ACTION_MAX_LENGTH,
                "Sync item action"
            );

        String errorCode =
            normalizeOptional(
                command.errorCode(),
                ERROR_CODE_MAX_LENGTH,
                "Sync item error code"
            );

        String errorMessage =
            normalizeOptionalText(
                command.errorMessage()
            );

        String payloadJson =
            canonicalNullableJson(
                command.payloadJson(),
                "Sync item payload"
            );

        lockRun(
            organizationId,
            command.syncRunId()
        );

        IntegrationSyncItemResponse existing =
            findItemById(
                organizationId,
                itemId
            );

        if (existing != null) {

            if (
                sameItemPayload(
                    existing,
                    command.syncRunId(),
                    entityType,
                    externalId,
                    command.localEntityId(),
                    command.status(),
                    action,
                    errorCode,
                    errorMessage,
                    payloadJson
                )
            ) {

                return replayItem(
                    existing
                );
            }

            throw new IntegrationConflictException(
                "Integration sync item identifier is already used by another payload."
            );
        }

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO integration_sync_items(
                    id,
                    sync_run_id,
                    entity_type,
                    external_id,
                    local_entity_id,
                    status,
                    action,
                    error_code,
                    error_message,
                    payload
                )
                VALUES(
                    ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    CAST(? AS JSONB)
                )
                ON CONFLICT (id)
                DO NOTHING
                """,
                itemId,
                command.syncRunId(),
                entityType,
                externalId,
                command.localEntityId(),
                command.status().name(),
                action,
                errorCode,
                errorMessage,
                payloadJson
            );

        IntegrationSyncItemResponse stored =
            findItemById(
                organizationId,
                itemId
            );

        if (inserted == 0) {

            if (
                stored != null
                && sameItemPayload(
                    stored,
                    command.syncRunId(),
                    entityType,
                    externalId,
                    command.localEntityId(),
                    command.status(),
                    action,
                    errorCode,
                    errorMessage,
                    payloadJson
                )
            ) {

                return replayItem(
                    stored
                );
            }

            throw new IntegrationConflictException(
                "Integration sync item identifier conflicts with another payload."
            );
        }

        if (stored == null) {

            throw new IntegrationConflictException(
                "Integration sync item could not be resolved after creation."
            );
        }

        return stored;
    }

    @Transactional(readOnly = true)
    public IntegrationSyncRunResponse getRun(
        UUID organizationId,
        UUID syncRunId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            syncRunId,
            "Sync run id"
        );

        IntegrationSyncRunResponse response =
            findRunById(
                organizationId,
                syncRunId
            );

        if (response == null) {

            throw new IntegrationNotFoundException(
                "Integration sync run does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<IntegrationSyncRunResponse> listRunsByStatus(
        UUID organizationId,
        UUID connectorId,
        IntegrationSyncRunStatus status
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
                "Sync run status is required."
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                r.id,
                r.connector_id,
                r.sync_type,
                r.status,
                r.started_at,
                r.completed_at,
                r.processed_count,
                r.success_count,
                r.failure_count,
                r.initiated_by,
                r.error_summary
            FROM integration_sync_runs r
            JOIN integration_connectors c
              ON c.id = r.connector_id
            WHERE r.connector_id = ?
              AND c.organization_id = ?
              AND r.status = ?
              AND (
                    r.initiated_by IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM users u
                        WHERE u.id = r.initiated_by
                          AND u.organization_id = ?
                    )
                  )
            ORDER BY
                r.started_at DESC,
                r.id
            """,
            (rs, rowNum) ->
                mapRun(
                    rs,
                    false
                ),
            connectorId,
            organizationId,
            status.name(),
            organizationId
        );
    }

    @Transactional(readOnly = true)
    public List<IntegrationSyncItemResponse> listItems(
        UUID organizationId,
        UUID syncRunId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            syncRunId,
            "Sync run id"
        );

        return jdbcTemplate.query(
            """
            SELECT
                i.id,
                i.sync_run_id,
                i.entity_type,
                i.external_id,
                i.local_entity_id,
                i.status,
                i.action,
                i.error_code,
                i.error_message,
                i.payload::text AS payload_json,
                i.processed_at
            FROM integration_sync_items i
            JOIN integration_sync_runs r
              ON r.id = i.sync_run_id
            JOIN integration_connectors c
              ON c.id = r.connector_id
            WHERE i.sync_run_id = ?
              AND c.organization_id = ?
            ORDER BY
                i.processed_at,
                i.id
            """,
            (rs, rowNum) ->
                mapItem(
                    rs,
                    false
                ),
            syncRunId,
            organizationId
        );
    }

    private IntegrationSyncRunResponse findRunById(
        UUID organizationId,
        UUID syncRunId
    ) {

        List<IntegrationSyncRunResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    r.id,
                    r.connector_id,
                    r.sync_type,
                    r.status,
                    r.started_at,
                    r.completed_at,
                    r.processed_count,
                    r.success_count,
                    r.failure_count,
                    r.initiated_by,
                    r.error_summary
                FROM integration_sync_runs r
                JOIN integration_connectors c
                  ON c.id = r.connector_id
                WHERE r.id = ?
                  AND c.organization_id = ?
                  AND (
                        r.initiated_by IS NULL
                        OR EXISTS (
                            SELECT 1
                            FROM users u
                            WHERE u.id = r.initiated_by
                              AND u.organization_id = ?
                        )
                      )
                """,
                (rs, rowNum) ->
                    mapRun(
                        rs,
                        false
                    ),
                syncRunId,
                organizationId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(
            0
        );
    }

    private IntegrationSyncItemResponse findItemById(
        UUID organizationId,
        UUID itemId
    ) {

        List<IntegrationSyncItemResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    i.id,
                    i.sync_run_id,
                    i.entity_type,
                    i.external_id,
                    i.local_entity_id,
                    i.status,
                    i.action,
                    i.error_code,
                    i.error_message,
                    i.payload::text AS payload_json,
                    i.processed_at
                FROM integration_sync_items i
                JOIN integration_sync_runs r
                  ON r.id = i.sync_run_id
                JOIN integration_connectors c
                  ON c.id = r.connector_id
                WHERE i.id = ?
                  AND c.organization_id = ?
                """,
                (rs, rowNum) ->
                    mapItem(
                        rs,
                        false
                    ),
                itemId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(
            0
        );
    }

    private IntegrationSyncRunResponse mapRun(
        ResultSet rs,
        boolean replayed
    ) throws SQLException {

        return new IntegrationSyncRunResponse(
            rs.getObject(
                "id",
                UUID.class
            ),
            rs.getObject(
                "connector_id",
                UUID.class
            ),
            rs.getString(
                "sync_type"
            ),
            IntegrationSyncRunStatus.valueOf(
                rs.getString(
                    "status"
                )
            ),
            rs.getObject(
                "started_at",
                OffsetDateTime.class
            ),
            rs.getObject(
                "completed_at",
                OffsetDateTime.class
            ),
            rs.getInt(
                "processed_count"
            ),
            rs.getInt(
                "success_count"
            ),
            rs.getInt(
                "failure_count"
            ),
            rs.getObject(
                "initiated_by",
                UUID.class
            ),
            rs.getString(
                "error_summary"
            ),
            replayed
        );
    }

    private IntegrationSyncItemResponse mapItem(
        ResultSet rs,
        boolean replayed
    ) throws SQLException {

        return new IntegrationSyncItemResponse(
            rs.getObject(
                "id",
                UUID.class
            ),
            rs.getObject(
                "sync_run_id",
                UUID.class
            ),
            rs.getString(
                "entity_type"
            ),
            rs.getString(
                "external_id"
            ),
            rs.getObject(
                "local_entity_id",
                UUID.class
            ),
            IntegrationSyncItemStatus.valueOf(
                rs.getString(
                    "status"
                )
            ),
            rs.getString(
                "action"
            ),
            rs.getString(
                "error_code"
            ),
            rs.getString(
                "error_message"
            ),
            rs.getString(
                "payload_json"
            ),
            rs.getObject(
                "processed_at",
                OffsetDateTime.class
            ),
            replayed
        );
    }

    private IntegrationSyncRunResponse replayRun(
        IntegrationSyncRunResponse response
    ) {

        return new IntegrationSyncRunResponse(
            response.id(),
            response.connectorId(),
            response.syncType(),
            response.status(),
            response.startedAt(),
            response.completedAt(),
            response.processedCount(),
            response.successCount(),
            response.failureCount(),
            response.initiatedBy(),
            response.errorSummary(),
            true
        );
    }

    private IntegrationSyncItemResponse replayItem(
        IntegrationSyncItemResponse response
    ) {

        return new IntegrationSyncItemResponse(
            response.id(),
            response.syncRunId(),
            response.entityType(),
            response.externalId(),
            response.localEntityId(),
            response.status(),
            response.action(),
            response.errorCode(),
            response.errorMessage(),
            response.payloadJson(),
            response.processedAt(),
            true
        );
    }

    private boolean sameRunRequest(
        IntegrationSyncRunResponse existing,
        UUID connectorId,
        String syncType,
        UUID initiatedBy
    ) {

        return existing.connectorId()
            .equals(
                connectorId
            )
            && existing.syncType()
                .equals(
                    syncType
                )
            && Objects.equals(
                existing.initiatedBy(),
                initiatedBy
            );
    }

    private boolean sameItemPayload(
        IntegrationSyncItemResponse existing,
        UUID syncRunId,
        String entityType,
        String externalId,
        UUID localEntityId,
        IntegrationSyncItemStatus status,
        String action,
        String errorCode,
        String errorMessage,
        String payloadJson
    ) {

        return existing.syncRunId()
            .equals(
                syncRunId
            )
            && Objects.equals(
                existing.entityType(),
                entityType
            )
            && Objects.equals(
                existing.externalId(),
                externalId
            )
            && Objects.equals(
                existing.localEntityId(),
                localEntityId
            )
            && existing.status()
                == status
            && Objects.equals(
                existing.action(),
                action
            )
            && Objects.equals(
                existing.errorCode(),
                errorCode
            )
            && Objects.equals(
                existing.errorMessage(),
                errorMessage
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

    private void lockRun(
        UUID organizationId,
        UUID syncRunId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT r.id
                FROM integration_sync_runs r
                JOIN integration_connectors c
                  ON c.id = r.connector_id
                WHERE r.id = ?
                  AND c.organization_id = ?
                FOR UPDATE OF r
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                syncRunId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new IntegrationNotFoundException(
                "Integration sync run does not exist."
            );
        }
    }

    private void lockOptionalUser(
        UUID organizationId,
        UUID userId
    ) {

        if (userId == null) {
            return;
        }

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM users
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                userId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new IntegrationNotFoundException(
                "Sync initiator does not exist."
            );
        }
    }

    private String canonicalNullableJson(
        String value,
        String label
    ) {

        if (value == null) {
            return null;
        }

        if (value.trim().isEmpty()) {

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
                "Stored sync item JSON is invalid."
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

    private String normalizeOptionalText(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {
            return null;
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