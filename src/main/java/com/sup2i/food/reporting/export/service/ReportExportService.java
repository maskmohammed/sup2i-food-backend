package com.sup2i.food.reporting.export.service;

import com.sup2i.food.reporting.export.api.dto.CreateReportExportCommand;
import com.sup2i.food.reporting.export.api.dto.ReportExportResponse;
import com.sup2i.food.reporting.export.domain.ReportExportStatus;
import com.sup2i.food.reporting.export.domain.ReportExportType;
import com.sup2i.food.reporting.export.exception.ReportExportConflictException;
import com.sup2i.food.reporting.export.exception.ReportExportNotFoundException;
import com.sup2i.food.reporting.export.exception.ReportExportValidationException;

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
public class ReportExportService {

    private final JdbcTemplate jdbcTemplate;

    public ReportExportService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public ReportExportResponse request(
        UUID organizationId,
        UUID exportId,
        CreateReportExportCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            exportId,
            "Export id"
        );

        validateCommand(
            command
        );

        String parametersJson =
            canonicalNullableJson(
                command.parametersJson(),
                "Export parameters"
            );

        lockOrganization(
            organizationId
        );

        lockRequester(
            organizationId,
            command.requestedBy()
        );

        lockOptionalSnapshot(
            organizationId,
            command.reportSnapshotId()
        );

        ReportExportResponse existing =
            findById(
                organizationId,
                exportId
            );

        if (existing != null) {

            if (
                sameRequestPayload(
                    existing,
                    command,
                    parametersJson
                )
            ) {
                return replay(
                    existing
                );
            }

            throw new ReportExportConflictException(
                "Report export identifier is already used by another payload."
            );
        }

        int inserted;

        try {

            inserted =
                jdbcTemplate.update(
                    """
                    INSERT INTO report_exports(
                        id,
                        report_snapshot_id,
                        organization_id,
                        export_type,
                        status,
                        requested_by,
                        parameters
                    )
                    VALUES(
                        ?, ?, ?, ?, 'PENDING', ?,
                        CAST(? AS JSONB)
                    )
                    ON CONFLICT (id)
                    DO NOTHING
                    """,
                    exportId,
                    command.reportSnapshotId(),
                    organizationId,
                    command.exportType().name(),
                    command.requestedBy(),
                    parametersJson
                );

        } catch (DataAccessException exception) {

            throw new ReportExportConflictException(
                "Report export conflicts with an existing database resource."
            );
        }

        ReportExportResponse stored =
            findById(
                organizationId,
                exportId
            );

        if (stored == null) {

            throw new ReportExportConflictException(
                "Report export identifier conflicts with another tenant resource."
            );
        }

        if (inserted == 0) {

            if (
                sameRequestPayload(
                    stored,
                    command,
                    parametersJson
                )
            ) {
                return replay(
                    stored
                );
            }

            throw new ReportExportConflictException(
                "Report export identifier is already used by another payload."
            );
        }

        return stored;
    }

    @Transactional(readOnly = true)
    public ReportExportResponse get(
        UUID organizationId,
        UUID exportId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            exportId,
            "Export id"
        );

        ReportExportResponse response =
            findById(
                organizationId,
                exportId
            );

        if (response == null) {

            throw new ReportExportNotFoundException(
                "Report export does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<ReportExportResponse> listByStatus(
        UUID organizationId,
        ReportExportStatus status
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        if (status == null) {

            throw new ReportExportValidationException(
                "Report export status is required."
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                e.id,
                e.report_snapshot_id,
                e.organization_id,
                e.export_type,
                e.status,
                e.requested_by,
                e.requested_at,
                e.completed_at,
                e.file_asset_id,
                e.parameters::text AS parameters_json,
                e.error_message
            FROM report_exports e
            WHERE e.organization_id = ?
              AND e.status = ?
              AND EXISTS (
                    SELECT 1
                    FROM users u
                    WHERE u.id = e.requested_by
                      AND u.organization_id = ?
                  )
              AND (
                    e.report_snapshot_id IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM report_snapshots s
                        WHERE s.id = e.report_snapshot_id
                          AND s.organization_id = ?
                    )
                  )
              AND (
                    e.file_asset_id IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM file_assets fa
                        WHERE fa.id = e.file_asset_id
                          AND fa.organization_id = ?
                    )
                  )
            ORDER BY
                e.requested_at DESC,
                e.id
            """,
            (rs, rowNum) ->
                mapRow(
                    rs,
                    false
                ),
            organizationId,
            status.name(),
            organizationId,
            organizationId,
            organizationId
        );
    }

    private ReportExportResponse findById(
        UUID organizationId,
        UUID exportId
    ) {

        List<ReportExportResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    e.id,
                    e.report_snapshot_id,
                    e.organization_id,
                    e.export_type,
                    e.status,
                    e.requested_by,
                    e.requested_at,
                    e.completed_at,
                    e.file_asset_id,
                    e.parameters::text AS parameters_json,
                    e.error_message
                FROM report_exports e
                WHERE e.id = ?
                  AND e.organization_id = ?
                  AND EXISTS (
                        SELECT 1
                        FROM users u
                        WHERE u.id = e.requested_by
                          AND u.organization_id = ?
                      )
                  AND (
                        e.report_snapshot_id IS NULL
                        OR EXISTS (
                            SELECT 1
                            FROM report_snapshots s
                            WHERE s.id = e.report_snapshot_id
                              AND s.organization_id = ?
                        )
                      )
                  AND (
                        e.file_asset_id IS NULL
                        OR EXISTS (
                            SELECT 1
                            FROM file_assets fa
                            WHERE fa.id = e.file_asset_id
                              AND fa.organization_id = ?
                        )
                      )
                """,
                (rs, rowNum) ->
                    mapRow(
                        rs,
                        false
                    ),
                exportId,
                organizationId,
                organizationId,
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

    private ReportExportResponse mapRow(
        ResultSet rs,
        boolean replayed
    ) throws SQLException {

        return new ReportExportResponse(
            rs.getObject(
                "id",
                UUID.class
            ),
            rs.getObject(
                "report_snapshot_id",
                UUID.class
            ),
            rs.getObject(
                "organization_id",
                UUID.class
            ),
            ReportExportType.valueOf(
                rs.getString(
                    "export_type"
                )
            ),
            ReportExportStatus.valueOf(
                rs.getString(
                    "status"
                )
            ),
            rs.getObject(
                "requested_by",
                UUID.class
            ),
            rs.getObject(
                "requested_at",
                OffsetDateTime.class
            ),
            rs.getObject(
                "completed_at",
                OffsetDateTime.class
            ),
            rs.getObject(
                "file_asset_id",
                UUID.class
            ),
            rs.getString(
                "parameters_json"
            ),
            rs.getString(
                "error_message"
            ),
            replayed
        );
    }

    private ReportExportResponse replay(
        ReportExportResponse response
    ) {

        return new ReportExportResponse(
            response.id(),
            response.reportSnapshotId(),
            response.organizationId(),
            response.exportType(),
            response.status(),
            response.requestedBy(),
            response.requestedAt(),
            response.completedAt(),
            response.fileAssetId(),
            response.parametersJson(),
            response.errorMessage(),
            true
        );
    }

    private boolean sameRequestPayload(
        ReportExportResponse existing,
        CreateReportExportCommand command,
        String parametersJson
    ) {

        return Objects.equals(
            existing.reportSnapshotId(),
            command.reportSnapshotId()
        )
            && existing.exportType()
                == command.exportType()
            && existing.requestedBy()
                .equals(
                    command.requestedBy()
                )
            && sameJson(
                existing.parametersJson(),
                parametersJson
            );
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

            throw new ReportExportConflictException(
                "Stored report export JSON is invalid."
            );
        }
    }

    private void validateCommand(
        CreateReportExportCommand command
    ) {

        if (command == null) {

            throw new ReportExportValidationException(
                "Report export command is required."
            );
        }

        if (command.exportType() == null) {

            throw new ReportExportValidationException(
                "Report export type is required."
            );
        }

        requireId(
            command.requestedBy(),
            "Requested by"
        );
    }

    private String canonicalNullableJson(
        String value,
        String label
    ) {

        if (value == null) {
            return null;
        }

        if (value.trim().isEmpty()) {

            throw new ReportExportValidationException(
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

                throw new ReportExportValidationException(
                    label + " must contain valid JSON."
                );
            }

            return canonical;

        } catch (DataAccessException exception) {

            throw new ReportExportValidationException(
                label + " must contain valid JSON."
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

            throw new ReportExportNotFoundException(
                "Organization does not exist."
            );
        }
    }

    private void lockRequester(
        UUID organizationId,
        UUID requestedBy
    ) {

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
                requestedBy,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new ReportExportNotFoundException(
                "Report export requester does not exist."
            );
        }
    }

    private void lockOptionalSnapshot(
        UUID organizationId,
        UUID reportSnapshotId
    ) {

        if (reportSnapshotId == null) {
            return;
        }

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM report_snapshots
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                reportSnapshotId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new ReportExportNotFoundException(
                "Report snapshot does not exist."
            );
        }
    }

    private void requireId(
        UUID id,
        String label
    ) {

        if (id == null) {

            throw new ReportExportValidationException(
                label + " is required."
            );
        }
    }
}