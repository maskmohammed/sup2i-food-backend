package com.sup2i.food.integration.service;

import com.sup2i.food.integration.api.dto.CreateIntegrationConnectorCommand;
import com.sup2i.food.integration.api.dto.IntegrationConnectorResponse;
import com.sup2i.food.integration.domain.IntegrationConnectorStatus;
import com.sup2i.food.integration.domain.IntegrationConnectorType;
import com.sup2i.food.integration.domain.IntegrationDirection;
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
public class IntegrationConnectorService {

    private static final int CODE_MAX_LENGTH =
        80;

    private static final int SECRET_REF_MAX_LENGTH =
        500;

    private final JdbcTemplate jdbcTemplate;

    public IntegrationConnectorService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public IntegrationConnectorResponse create(
        UUID organizationId,
        UUID connectorId,
        CreateIntegrationConnectorCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            connectorId,
            "Connector id"
        );

        if (command == null) {

            throw new IntegrationValidationException(
                "Integration connector command is required."
            );
        }

        String code =
            normalizeRequired(
                command.code(),
                CODE_MAX_LENGTH,
                "Connector code"
            );

        if (command.connectorType() == null) {

            throw new IntegrationValidationException(
                "Connector type is required."
            );
        }

        IntegrationDirection direction =
            command.direction();

        if (direction == null) {

            direction =
                IntegrationDirection.BIDIRECTIONAL;
        }

        String configJson =
            canonicalConfig(
                command.configJson()
            );

        String secretRef =
            normalizeOptional(
                command.secretRef(),
                SECRET_REF_MAX_LENGTH,
                "Connector secret reference"
            );

        lockOrganization(
            organizationId
        );

        ConnectorRow existing =
            findRowById(
                organizationId,
                connectorId
            );

        if (existing != null) {

            if (
                sameCreatePayload(
                    existing,
                    code,
                    command.connectorType(),
                    direction,
                    configJson,
                    secretRef
                )
            ) {

                return toResponse(
                    existing,
                    true
                );
            }

            throw new IntegrationConflictException(
                "Integration connector identifier is already used by another payload."
            );
        }

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO integration_connectors(
                    id,
                    organization_id,
                    code,
                    connector_type,
                    direction,
                    status,
                    config,
                    secret_ref
                )
                VALUES(
                    ?, ?, ?, ?, ?,
                    'DISABLED',
                    CAST(? AS JSONB),
                    ?
                )
                ON CONFLICT
                DO NOTHING
                """,
                connectorId,
                organizationId,
                code,
                command.connectorType().name(),
                direction.name(),
                configJson,
                secretRef
            );

        ConnectorRow stored =
            findRowById(
                organizationId,
                connectorId
            );

        if (inserted == 0) {

            if (
                stored != null
                && sameCreatePayload(
                    stored,
                    code,
                    command.connectorType(),
                    direction,
                    configJson,
                    secretRef
                )
            ) {

                return toResponse(
                    stored,
                    true
                );
            }

            throw new IntegrationConflictException(
                "Integration connector conflicts with an existing identifier or organization code."
            );
        }

        if (stored == null) {

            throw new IntegrationConflictException(
                "Integration connector could not be resolved after creation."
            );
        }

        return toResponse(
            stored,
            false
        );
    }

    @Transactional(readOnly = true)
    public IntegrationConnectorResponse get(
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

        ConnectorRow row =
            findRowById(
                organizationId,
                connectorId
            );

        if (row == null) {

            throw new IntegrationNotFoundException(
                "Integration connector does not exist."
            );
        }

        return toResponse(
            row,
            false
        );
    }

    @Transactional(readOnly = true)
    public List<IntegrationConnectorResponse> list(
        UUID organizationId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        return jdbcTemplate.query(
            """
            SELECT
                c.id,
                c.organization_id,
                c.code,
                c.connector_type,
                c.direction,
                c.status,
                c.config::text AS config_json,
                c.secret_ref,
                c.last_success_at,
                c.last_error_at,
                c.created_at,
                c.updated_at
            FROM integration_connectors c
            WHERE c.organization_id = ?
            ORDER BY
                c.code,
                c.id
            """,
            (rs, rowNum) ->
                toResponse(
                    mapRow(
                        rs
                    ),
                    false
                ),
            organizationId
        );
    }

    private ConnectorRow findRowById(
        UUID organizationId,
        UUID connectorId
    ) {

        List<ConnectorRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    c.id,
                    c.organization_id,
                    c.code,
                    c.connector_type,
                    c.direction,
                    c.status,
                    c.config::text AS config_json,
                    c.secret_ref,
                    c.last_success_at,
                    c.last_error_at,
                    c.created_at,
                    c.updated_at
                FROM integration_connectors c
                WHERE c.id = ?
                  AND c.organization_id = ?
                """,
                (rs, rowNum) ->
                    mapRow(
                        rs
                    ),
                connectorId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(
            0
        );
    }

    private ConnectorRow mapRow(
        ResultSet rs
    ) throws SQLException {

        return new ConnectorRow(
            rs.getObject(
                "id",
                UUID.class
            ),
            rs.getObject(
                "organization_id",
                UUID.class
            ),
            rs.getString(
                "code"
            ),
            IntegrationConnectorType.valueOf(
                rs.getString(
                    "connector_type"
                )
            ),
            IntegrationDirection.valueOf(
                rs.getString(
                    "direction"
                )
            ),
            IntegrationConnectorStatus.valueOf(
                rs.getString(
                    "status"
                )
            ),
            rs.getString(
                "config_json"
            ),
            rs.getString(
                "secret_ref"
            ),
            rs.getObject(
                "last_success_at",
                OffsetDateTime.class
            ),
            rs.getObject(
                "last_error_at",
                OffsetDateTime.class
            ),
            rs.getObject(
                "created_at",
                OffsetDateTime.class
            ),
            rs.getObject(
                "updated_at",
                OffsetDateTime.class
            )
        );
    }

    private IntegrationConnectorResponse toResponse(
        ConnectorRow row,
        boolean replayed
    ) {

        return new IntegrationConnectorResponse(
            row.id(),
            row.organizationId(),
            row.code(),
            row.connectorType(),
            row.direction(),
            row.status(),
            row.configJson(),
            row.secretRef() != null,
            row.lastSuccessAt(),
            row.lastErrorAt(),
            row.createdAt(),
            row.updatedAt(),
            replayed
        );
    }

    private boolean sameCreatePayload(
        ConnectorRow row,
        String code,
        IntegrationConnectorType connectorType,
        IntegrationDirection direction,
        String configJson,
        String secretRef
    ) {

        return row.code()
            .equals(
                code
            )
            && row.connectorType()
                == connectorType
            && row.direction()
                == direction
            && sameJson(
                row.configJson(),
                configJson
            )
            && Objects.equals(
                row.secretRef(),
                secretRef
            );
    }

    private String canonicalConfig(
        String value
    ) {

        if (value == null) {
            return "{}";
        }

        if (value.trim().isEmpty()) {

            throw new IntegrationValidationException(
                "Connector config must contain valid JSON."
            );
        }

        return canonicalRequiredJson(
            value,
            "Connector config"
        );
    }

    private String canonicalRequiredJson(
        String value,
        String label
    ) {

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
                "Stored connector JSON is invalid."
            );
        }
    }

    private void lockOrganization(
        UUID organizationId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM organizations
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                organizationId
            );

        if (rows.isEmpty()) {

            throw new IntegrationNotFoundException(
                "Organization does not exist."
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

    private record ConnectorRow(
        UUID id,
        UUID organizationId,
        String code,
        IntegrationConnectorType connectorType,
        IntegrationDirection direction,
        IntegrationConnectorStatus status,
        String configJson,
        String secretRef,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime lastErrorAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }
}