package com.sup2i.food.integration.service;

import com.sup2i.food.integration.api.dto.CreateImportJobCommand;
import com.sup2i.food.integration.api.dto.ImportJobResponse;
import com.sup2i.food.integration.api.dto.ImportJobRowResponse;
import com.sup2i.food.integration.api.dto.RecordImportJobRowCommand;
import com.sup2i.food.integration.domain.ImportJobStatus;
import com.sup2i.food.integration.domain.ImportRowStatus;
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
public class ImportJobService {

    private final JdbcTemplate jdbcTemplate;

    public ImportJobService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public ImportJobResponse createJob(
        UUID organizationId,
        UUID importJobId,
        CreateImportJobCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            importJobId,
            "Import job id"
        );

        if (command == null) {

            throw new IntegrationValidationException(
                "Import job command is required."
            );
        }

        if (command.importType() == null) {

            throw new IntegrationValidationException(
                "Import type is required."
            );
        }

        requireId(
            command.requestedBy(),
            "Import requester"
        );

        lockOrganization(
            organizationId
        );

        lockRequester(
            organizationId,
            command.requestedBy()
        );

        lockOptionalFileAsset(
            organizationId,
            command.sourceFileAssetId()
        );

        ImportJobResponse existing =
            findJobById(
                organizationId,
                importJobId
            );

        if (existing != null) {

            if (
                sameJobRequest(
                    existing,
                    command
                )
            ) {

                return replayJob(
                    existing
                );
            }

            throw new IntegrationConflictException(
                "Import job identifier is already used by another request."
            );
        }

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO import_jobs(
                    id,
                    organization_id,
                    import_type,
                    source_file_asset_id,
                    status,
                    requested_by,
                    total_rows,
                    success_rows,
                    failed_rows
                )
                VALUES(
                    ?, ?, ?, ?,
                    'PENDING',
                    ?,
                    0, 0, 0
                )
                ON CONFLICT (id)
                DO NOTHING
                """,
                importJobId,
                organizationId,
                command.importType().name(),
                command.sourceFileAssetId(),
                command.requestedBy()
            );

        ImportJobResponse stored =
            findJobById(
                organizationId,
                importJobId
            );

        if (inserted == 0) {

            if (
                stored != null
                && sameJobRequest(
                    stored,
                    command
                )
            ) {

                return replayJob(
                    stored
                );
            }

            throw new IntegrationConflictException(
                "Import job identifier conflicts with another request."
            );
        }

        if (stored == null) {

            throw new IntegrationConflictException(
                "Import job could not be resolved after creation."
            );
        }

        return stored;
    }

    @Transactional
    public ImportJobRowResponse recordRow(
        UUID organizationId,
        UUID rowId,
        RecordImportJobRowCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            rowId,
            "Import row id"
        );

        if (command == null) {

            throw new IntegrationValidationException(
                "Import row command is required."
            );
        }

        requireId(
            command.importJobId(),
            "Import job id"
        );

        if (command.rowNumber() <= 0) {

            throw new IntegrationValidationException(
                "Import row number must be greater than zero."
            );
        }

        String rawDataJson =
            canonicalRequiredJson(
                command.rawDataJson(),
                "Import row raw data"
            );

        lockJob(
            organizationId,
            command.importJobId()
        );

        ImportJobRowResponse existingById =
            findRowById(
                organizationId,
                rowId
            );

        if (existingById != null) {

            if (
                sameRowRequest(
                    existingById,
                    command.importJobId(),
                    command.rowNumber(),
                    rawDataJson
                )
            ) {

                return replayRow(
                    existingById
                );
            }

            throw new IntegrationConflictException(
                "Import row identifier is already used by another request."
            );
        }

        ImportJobRowResponse existingNatural =
            findRowByNumber(
                organizationId,
                command.importJobId(),
                command.rowNumber()
            );

        if (existingNatural != null) {

            if (
                sameRowRequest(
                    existingNatural,
                    command.importJobId(),
                    command.rowNumber(),
                    rawDataJson
                )
            ) {

                return replayRow(
                    existingNatural
                );
            }

            throw new IntegrationConflictException(
                "Import row number is already used by another payload."
            );
        }

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO import_job_rows(
                    id,
                    import_job_id,
                    row_number,
                    raw_data,
                    status
                )
                VALUES(
                    ?, ?, ?,
                    CAST(? AS JSONB),
                    'PENDING'
                )
                ON CONFLICT
                DO NOTHING
                """,
                rowId,
                command.importJobId(),
                command.rowNumber(),
                rawDataJson
            );

        ImportJobRowResponse stored =
            findRowById(
                organizationId,
                rowId
            );

        if (inserted == 0) {

            if (
                stored != null
                && sameRowRequest(
                    stored,
                    command.importJobId(),
                    command.rowNumber(),
                    rawDataJson
                )
            ) {

                return replayRow(
                    stored
                );
            }

            ImportJobRowResponse naturalStored =
                findRowByNumber(
                    organizationId,
                    command.importJobId(),
                    command.rowNumber()
                );

            if (
                naturalStored != null
                && sameRowRequest(
                    naturalStored,
                    command.importJobId(),
                    command.rowNumber(),
                    rawDataJson
                )
            ) {

                return replayRow(
                    naturalStored
                );
            }

            throw new IntegrationConflictException(
                "Import row conflicts with an existing job row."
            );
        }

        if (stored == null) {

            throw new IntegrationConflictException(
                "Import row could not be resolved after creation."
            );
        }

        return stored;
    }

    @Transactional(readOnly = true)
    public ImportJobResponse getJob(
        UUID organizationId,
        UUID importJobId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            importJobId,
            "Import job id"
        );

        ImportJobResponse response =
            findJobById(
                organizationId,
                importJobId
            );

        if (response == null) {

            throw new IntegrationNotFoundException(
                "Import job does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<ImportJobResponse> listJobsByStatus(
        UUID organizationId,
        ImportJobStatus status
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        if (status == null) {

            throw new IntegrationValidationException(
                "Import job status is required."
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                j.id,
                j.organization_id,
                j.import_type,
                j.source_file_asset_id,
                j.status,
                j.requested_by,
                j.created_at,
                j.started_at,
                j.completed_at,
                j.total_rows,
                j.success_rows,
                j.failed_rows,
                j.error_summary
            FROM import_jobs j
            WHERE j.organization_id = ?
              AND j.status = ?
              AND EXISTS (
                    SELECT 1
                    FROM users u
                    WHERE u.id = j.requested_by
                      AND u.organization_id = ?
                  )
              AND (
                    j.source_file_asset_id IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM file_assets f
                        WHERE f.id = j.source_file_asset_id
                          AND f.organization_id = ?
                    )
                  )
            ORDER BY
                j.created_at DESC,
                j.id
            """,
            (rs, rowNum) ->
                mapJob(
                    rs,
                    false
                ),
            organizationId,
            status.name(),
            organizationId,
            organizationId
        );
    }

    @Transactional(readOnly = true)
    public List<ImportJobRowResponse> listRows(
        UUID organizationId,
        UUID importJobId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            importJobId,
            "Import job id"
        );

        return jdbcTemplate.query(
            """
            SELECT
                r.id,
                r.import_job_id,
                r.row_number,
                r.raw_data::text AS raw_data_json,
                r.status,
                r.local_entity_id,
                r.error_code,
                r.error_message,
                r.processed_at
            FROM import_job_rows r
            JOIN import_jobs j
              ON j.id = r.import_job_id
            WHERE r.import_job_id = ?
              AND j.organization_id = ?
            ORDER BY
                r.row_number,
                r.id
            """,
            (rs, rowNum) ->
                mapRow(
                    rs,
                    false
                ),
            importJobId,
            organizationId
        );
    }

    private ImportJobResponse findJobById(
        UUID organizationId,
        UUID importJobId
    ) {

        List<ImportJobResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    j.id,
                    j.organization_id,
                    j.import_type,
                    j.source_file_asset_id,
                    j.status,
                    j.requested_by,
                    j.created_at,
                    j.started_at,
                    j.completed_at,
                    j.total_rows,
                    j.success_rows,
                    j.failed_rows,
                    j.error_summary
                FROM import_jobs j
                WHERE j.id = ?
                  AND j.organization_id = ?
                  AND EXISTS (
                        SELECT 1
                        FROM users u
                        WHERE u.id = j.requested_by
                          AND u.organization_id = ?
                      )
                  AND (
                        j.source_file_asset_id IS NULL
                        OR EXISTS (
                            SELECT 1
                            FROM file_assets f
                            WHERE f.id = j.source_file_asset_id
                              AND f.organization_id = ?
                        )
                      )
                """,
                (rs, rowNum) ->
                    mapJob(
                        rs,
                        false
                    ),
                importJobId,
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

    private ImportJobRowResponse findRowById(
        UUID organizationId,
        UUID rowId
    ) {

        List<ImportJobRowResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    r.id,
                    r.import_job_id,
                    r.row_number,
                    r.raw_data::text AS raw_data_json,
                    r.status,
                    r.local_entity_id,
                    r.error_code,
                    r.error_message,
                    r.processed_at
                FROM import_job_rows r
                JOIN import_jobs j
                  ON j.id = r.import_job_id
                WHERE r.id = ?
                  AND j.organization_id = ?
                """,
                (rs, rowNum) ->
                    mapRow(
                        rs,
                        false
                    ),
                rowId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(
            0
        );
    }

    private ImportJobRowResponse findRowByNumber(
        UUID organizationId,
        UUID importJobId,
        int rowNumber
    ) {

        List<ImportJobRowResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    r.id,
                    r.import_job_id,
                    r.row_number,
                    r.raw_data::text AS raw_data_json,
                    r.status,
                    r.local_entity_id,
                    r.error_code,
                    r.error_message,
                    r.processed_at
                FROM import_job_rows r
                JOIN import_jobs j
                  ON j.id = r.import_job_id
                WHERE r.import_job_id = ?
                  AND r.row_number = ?
                  AND j.organization_id = ?
                """,
                (rs, rowNum) ->
                    mapRow(
                        rs,
                        false
                    ),
                importJobId,
                rowNumber,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(
            0
        );
    }

    private ImportJobResponse mapJob(
        ResultSet rs,
        boolean replayed
    ) throws SQLException {

        return new ImportJobResponse(
            rs.getObject(
                "id",
                UUID.class
            ),
            rs.getObject(
                "organization_id",
                UUID.class
            ),
            com.sup2i.food.integration.domain.ImportType.valueOf(
                rs.getString(
                    "import_type"
                )
            ),
            rs.getObject(
                "source_file_asset_id",
                UUID.class
            ),
            ImportJobStatus.valueOf(
                rs.getString(
                    "status"
                )
            ),
            rs.getObject(
                "requested_by",
                UUID.class
            ),
            rs.getObject(
                "created_at",
                OffsetDateTime.class
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
                "total_rows"
            ),
            rs.getInt(
                "success_rows"
            ),
            rs.getInt(
                "failed_rows"
            ),
            rs.getString(
                "error_summary"
            ),
            replayed
        );
    }

    private ImportJobRowResponse mapRow(
        ResultSet rs,
        boolean replayed
    ) throws SQLException {

        return new ImportJobRowResponse(
            rs.getObject(
                "id",
                UUID.class
            ),
            rs.getObject(
                "import_job_id",
                UUID.class
            ),
            rs.getInt(
                "row_number"
            ),
            rs.getString(
                "raw_data_json"
            ),
            ImportRowStatus.valueOf(
                rs.getString(
                    "status"
                )
            ),
            rs.getObject(
                "local_entity_id",
                UUID.class
            ),
            rs.getString(
                "error_code"
            ),
            rs.getString(
                "error_message"
            ),
            rs.getObject(
                "processed_at",
                OffsetDateTime.class
            ),
            replayed
        );
    }

    private ImportJobResponse replayJob(
        ImportJobResponse response
    ) {

        return new ImportJobResponse(
            response.id(),
            response.organizationId(),
            response.importType(),
            response.sourceFileAssetId(),
            response.status(),
            response.requestedBy(),
            response.createdAt(),
            response.startedAt(),
            response.completedAt(),
            response.totalRows(),
            response.successRows(),
            response.failedRows(),
            response.errorSummary(),
            true
        );
    }

    private ImportJobRowResponse replayRow(
        ImportJobRowResponse response
    ) {

        return new ImportJobRowResponse(
            response.id(),
            response.importJobId(),
            response.rowNumber(),
            response.rawDataJson(),
            response.status(),
            response.localEntityId(),
            response.errorCode(),
            response.errorMessage(),
            response.processedAt(),
            true
        );
    }

    private boolean sameJobRequest(
        ImportJobResponse existing,
        CreateImportJobCommand command
    ) {

        return existing.importType()
            == command.importType()
            && Objects.equals(
                existing.sourceFileAssetId(),
                command.sourceFileAssetId()
            )
            && existing.requestedBy()
                .equals(
                    command.requestedBy()
                );
    }

    private boolean sameRowRequest(
        ImportJobRowResponse existing,
        UUID importJobId,
        int rowNumber,
        String rawDataJson
    ) {

        return existing.importJobId()
            .equals(
                importJobId
            )
            && existing.rowNumber()
                == rowNumber
            && sameJson(
                existing.rawDataJson(),
                rawDataJson
            );
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

            throw new IntegrationNotFoundException(
                "Import requester does not exist."
            );
        }
    }

    private void lockOptionalFileAsset(
        UUID organizationId,
        UUID fileAssetId
    ) {

        if (fileAssetId == null) {
            return;
        }

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM file_assets
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                fileAssetId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new IntegrationNotFoundException(
                "Import source file asset does not exist."
            );
        }
    }

    private void lockJob(
        UUID organizationId,
        UUID importJobId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM import_jobs
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                importJobId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new IntegrationNotFoundException(
                "Import job does not exist."
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
                "Stored import row JSON is invalid."
            );
        }
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