package com.sup2i.food.integration.service;

import com.sup2i.food.integration.api.dto.ExternalEntityRefResponse;
import com.sup2i.food.integration.api.dto.RegisterExternalEntityRefCommand;
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
public class ExternalEntityRefService {

    private static final int ENTITY_TYPE_MAX_LENGTH =
        80;

    private static final int EXTERNAL_ID_MAX_LENGTH =
        255;

    private static final int EXTERNAL_VERSION_MAX_LENGTH =
        120;

    private final JdbcTemplate jdbcTemplate;

    public ExternalEntityRefService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public ExternalEntityRefResponse register(
        UUID organizationId,
        UUID referenceId,
        RegisterExternalEntityRefCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            referenceId,
            "External entity reference id"
        );

        if (command == null) {

            throw new IntegrationValidationException(
                "External entity reference command is required."
            );
        }

        requireId(
            command.connectorId(),
            "Connector id"
        );

        requireId(
            command.localEntityId(),
            "Local entity id"
        );

        String localEntityType =
            normalizeRequired(
                command.localEntityType(),
                ENTITY_TYPE_MAX_LENGTH,
                "Local entity type"
            );

        String externalEntityType =
            normalizeOptional(
                command.externalEntityType(),
                ENTITY_TYPE_MAX_LENGTH,
                "External entity type"
            );

        String externalId =
            normalizeRequired(
                command.externalId(),
                EXTERNAL_ID_MAX_LENGTH,
                "External id"
            );

        String externalVersion =
            normalizeOptional(
                command.externalVersion(),
                EXTERNAL_VERSION_MAX_LENGTH,
                "External version"
            );

        String metadataJson =
            canonicalNullableJson(
                command.metadataJson(),
                "External entity metadata"
            );

        lockConnector(
            organizationId,
            command.connectorId()
        );

        ExternalEntityRefResponse existing =
            findById(
                organizationId,
                referenceId
            );

        if (existing != null) {

            if (
                samePayload(
                    existing,
                    command.connectorId(),
                    localEntityType,
                    command.localEntityId(),
                    externalEntityType,
                    externalId,
                    externalVersion,
                    metadataJson
                )
            ) {

                return replay(
                    existing
                );
            }

            throw new IntegrationConflictException(
                "External entity reference identifier is already used by another payload."
            );
        }

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO external_entity_refs(
                    id,
                    connector_id,
                    local_entity_type,
                    local_entity_id,
                    external_entity_type,
                    external_id,
                    external_version,
                    metadata
                )
                VALUES(
                    ?, ?, ?, ?, ?, ?, ?,
                    CAST(? AS JSONB)
                )
                ON CONFLICT
                DO NOTHING
                """,
                referenceId,
                command.connectorId(),
                localEntityType,
                command.localEntityId(),
                externalEntityType,
                externalId,
                externalVersion,
                metadataJson
            );

        ExternalEntityRefResponse stored =
            findById(
                organizationId,
                referenceId
            );

        if (inserted == 0) {

            if (
                stored != null
                && samePayload(
                    stored,
                    command.connectorId(),
                    localEntityType,
                    command.localEntityId(),
                    externalEntityType,
                    externalId,
                    externalVersion,
                    metadataJson
                )
            ) {

                return replay(
                    stored
                );
            }

            throw new IntegrationConflictException(
                "External entity reference conflicts with an existing connector mapping."
            );
        }

        if (stored == null) {

            throw new IntegrationConflictException(
                "External entity reference could not be resolved after creation."
            );
        }

        return stored;
    }

    @Transactional(readOnly = true)
    public ExternalEntityRefResponse get(
        UUID organizationId,
        UUID referenceId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            referenceId,
            "External entity reference id"
        );

        ExternalEntityRefResponse response =
            findById(
                organizationId,
                referenceId
            );

        if (response == null) {

            throw new IntegrationNotFoundException(
                "External entity reference does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<ExternalEntityRefResponse> listForConnector(
        UUID organizationId,
        UUID connectorId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            connectorId,
            "Connector id"
        );

        return jdbcTemplate.query(
            """
            SELECT
                r.id,
                r.connector_id,
                r.local_entity_type,
                r.local_entity_id,
                r.external_entity_type,
                r.external_id,
                r.external_version,
                r.last_synced_at,
                r.metadata::text AS metadata_json
            FROM external_entity_refs r
            JOIN integration_connectors c
              ON c.id = r.connector_id
            WHERE r.connector_id = ?
              AND c.organization_id = ?
            ORDER BY
                r.local_entity_type,
                r.local_entity_id,
                r.id
            """,
            (rs, rowNum) ->
                mapRow(
                    rs,
                    false
                ),
            connectorId,
            organizationId
        );
    }

    private ExternalEntityRefResponse findById(
        UUID organizationId,
        UUID referenceId
    ) {

        List<ExternalEntityRefResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    r.id,
                    r.connector_id,
                    r.local_entity_type,
                    r.local_entity_id,
                    r.external_entity_type,
                    r.external_id,
                    r.external_version,
                    r.last_synced_at,
                    r.metadata::text AS metadata_json
                FROM external_entity_refs r
                JOIN integration_connectors c
                  ON c.id = r.connector_id
                WHERE r.id = ?
                  AND c.organization_id = ?
                """,
                (rs, rowNum) ->
                    mapRow(
                        rs,
                        false
                    ),
                referenceId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(
            0
        );
    }

    private ExternalEntityRefResponse mapRow(
        ResultSet rs,
        boolean replayed
    ) throws SQLException {

        return new ExternalEntityRefResponse(
            rs.getObject(
                "id",
                UUID.class
            ),
            rs.getObject(
                "connector_id",
                UUID.class
            ),
            rs.getString(
                "local_entity_type"
            ),
            rs.getObject(
                "local_entity_id",
                UUID.class
            ),
            rs.getString(
                "external_entity_type"
            ),
            rs.getString(
                "external_id"
            ),
            rs.getString(
                "external_version"
            ),
            rs.getObject(
                "last_synced_at",
                OffsetDateTime.class
            ),
            rs.getString(
                "metadata_json"
            ),
            replayed
        );
    }

    private ExternalEntityRefResponse replay(
        ExternalEntityRefResponse response
    ) {

        return new ExternalEntityRefResponse(
            response.id(),
            response.connectorId(),
            response.localEntityType(),
            response.localEntityId(),
            response.externalEntityType(),
            response.externalId(),
            response.externalVersion(),
            response.lastSyncedAt(),
            response.metadataJson(),
            true
        );
    }

    private boolean samePayload(
        ExternalEntityRefResponse existing,
        UUID connectorId,
        String localEntityType,
        UUID localEntityId,
        String externalEntityType,
        String externalId,
        String externalVersion,
        String metadataJson
    ) {

        return existing.connectorId()
            .equals(
                connectorId
            )
            && existing.localEntityType()
                .equals(
                    localEntityType
                )
            && existing.localEntityId()
                .equals(
                    localEntityId
                )
            && Objects.equals(
                existing.externalEntityType(),
                externalEntityType
            )
            && existing.externalId()
                .equals(
                    externalId
                )
            && Objects.equals(
                existing.externalVersion(),
                externalVersion
            )
            && sameJson(
                existing.metadataJson(),
                metadataJson
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
                "Stored external entity metadata JSON is invalid."
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