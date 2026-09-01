package com.sup2i.food.reporting.service;

import com.sup2i.food.reporting.api.dto.CreateReportSnapshotCommand;
import com.sup2i.food.reporting.api.dto.ReportSnapshotResponse;
import com.sup2i.food.reporting.exception.ReportSnapshotConflictException;
import com.sup2i.food.reporting.exception.ReportSnapshotNotFoundException;
import com.sup2i.food.reporting.exception.ReportSnapshotValidationException;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReportSnapshotService {

    private static final int REPORT_TYPE_MAX_LENGTH =
        60;

    private final JdbcTemplate jdbcTemplate;

    public ReportSnapshotService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public ReportSnapshotResponse create(
        UUID organizationId,
        UUID snapshotId,
        CreateReportSnapshotCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            snapshotId,
            "Snapshot id"
        );

        validateCommand(
            command
        );

        String reportType =
            normalizeReportType(
                command.reportType()
            );

        String dataJson =
            canonicalRequiredJson(
                command.dataJson(),
                "Report data"
            );

        lockOrganization(
            organizationId
        );

        lockScope(
            organizationId,
            command.campusId(),
            command.locationId(),
            command.generatedBy()
        );

        ReportSnapshotResponse existing =
            findById(
                organizationId,
                snapshotId
            );

        if (existing != null) {

            if (
                samePayload(
                    existing,
                    command,
                    reportType,
                    dataJson
                )
            ) {
                return replay(
                    existing
                );
            }

            throw new ReportSnapshotConflictException(
                "Report snapshot identifier is already used by another payload."
            );
        }

        int inserted;

        try {

            inserted =
                jdbcTemplate.update(
                    """
                    INSERT INTO report_snapshots(
                        id,
                        organization_id,
                        campus_id,
                        location_id,
                        report_type,
                        period_start,
                        period_end,
                        data,
                        generated_by
                    )
                    VALUES(
                        ?, ?, ?, ?, ?, ?, ?,
                        CAST(? AS JSONB),
                        ?
                    )
                    ON CONFLICT (id)
                    DO NOTHING
                    """,
                    snapshotId,
                    organizationId,
                    command.campusId(),
                    command.locationId(),
                    reportType,
                    command.periodStart(),
                    command.periodEnd(),
                    dataJson,
                    command.generatedBy()
                );

        } catch (DataAccessException exception) {

            throw new ReportSnapshotConflictException(
                "Report snapshot conflicts with an existing database resource."
            );
        }

        ReportSnapshotResponse stored =
            findById(
                organizationId,
                snapshotId
            );

        if (stored == null) {

            throw new ReportSnapshotConflictException(
                "Report snapshot identifier conflicts with another tenant resource."
            );
        }

        if (inserted == 0) {

            if (
                samePayload(
                    stored,
                    command,
                    reportType,
                    dataJson
                )
            ) {
                return replay(
                    stored
                );
            }

            throw new ReportSnapshotConflictException(
                "Report snapshot identifier is already used by another payload."
            );
        }

        return stored;
    }

    @Transactional(readOnly = true)
    public ReportSnapshotResponse get(
        UUID organizationId,
        UUID snapshotId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            snapshotId,
            "Snapshot id"
        );

        ReportSnapshotResponse response =
            findById(
                organizationId,
                snapshotId
            );

        if (response == null) {

            throw new ReportSnapshotNotFoundException(
                "Report snapshot does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<ReportSnapshotResponse> listForType(
        UUID organizationId,
        String reportType
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        String normalizedType =
            normalizeReportType(
                reportType
            );

        return jdbcTemplate.query(
            """
            SELECT
                s.id,
                s.organization_id,
                s.campus_id,
                s.location_id,
                s.report_type,
                s.period_start,
                s.period_end,
                s.data::text AS data_json,
                s.generated_by,
                s.generated_at
            FROM report_snapshots s
            WHERE s.organization_id = ?
              AND s.report_type = ?
              AND (
                    s.campus_id IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM campuses c
                        WHERE c.id = s.campus_id
                          AND c.organization_id = ?
                    )
                  )
              AND (
                    s.location_id IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM locations l
                        JOIN campuses c
                          ON c.id = l.campus_id
                        WHERE l.id = s.location_id
                          AND c.organization_id = ?
                    )
                  )
              AND (
                    s.generated_by IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM users u
                        WHERE u.id = s.generated_by
                          AND u.organization_id = ?
                    )
                  )
            ORDER BY
                s.generated_at DESC,
                s.id
            """,
            (rs, rowNum) ->
                mapRow(
                    rs,
                    false
                ),
            organizationId,
            normalizedType,
            organizationId,
            organizationId,
            organizationId
        );
    }

    private ReportSnapshotResponse findById(
        UUID organizationId,
        UUID snapshotId
    ) {

        List<ReportSnapshotResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    s.id,
                    s.organization_id,
                    s.campus_id,
                    s.location_id,
                    s.report_type,
                    s.period_start,
                    s.period_end,
                    s.data::text AS data_json,
                    s.generated_by,
                    s.generated_at
                FROM report_snapshots s
                WHERE s.id = ?
                  AND s.organization_id = ?
                  AND (
                        s.campus_id IS NULL
                        OR EXISTS (
                            SELECT 1
                            FROM campuses c
                            WHERE c.id = s.campus_id
                              AND c.organization_id = ?
                        )
                      )
                  AND (
                        s.location_id IS NULL
                        OR EXISTS (
                            SELECT 1
                            FROM locations l
                            JOIN campuses c
                              ON c.id = l.campus_id
                            WHERE l.id = s.location_id
                              AND c.organization_id = ?
                        )
                      )
                  AND (
                        s.generated_by IS NULL
                        OR EXISTS (
                            SELECT 1
                            FROM users u
                            WHERE u.id = s.generated_by
                              AND u.organization_id = ?
                        )
                      )
                """,
                (rs, rowNum) ->
                    mapRow(
                        rs,
                        false
                    ),
                snapshotId,
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

    private ReportSnapshotResponse mapRow(
        ResultSet rs,
        boolean replayed
    ) throws SQLException {

        return new ReportSnapshotResponse(
            rs.getObject(
                "id",
                UUID.class
            ),
            rs.getObject(
                "organization_id",
                UUID.class
            ),
            rs.getObject(
                "campus_id",
                UUID.class
            ),
            rs.getObject(
                "location_id",
                UUID.class
            ),
            rs.getString(
                "report_type"
            ),
            rs.getObject(
                "period_start",
                OffsetDateTime.class
            ),
            rs.getObject(
                "period_end",
                OffsetDateTime.class
            ),
            rs.getString(
                "data_json"
            ),
            rs.getObject(
                "generated_by",
                UUID.class
            ),
            rs.getObject(
                "generated_at",
                OffsetDateTime.class
            ),
            replayed
        );
    }

    private ReportSnapshotResponse replay(
        ReportSnapshotResponse response
    ) {

        return new ReportSnapshotResponse(
            response.id(),
            response.organizationId(),
            response.campusId(),
            response.locationId(),
            response.reportType(),
            response.periodStart(),
            response.periodEnd(),
            response.dataJson(),
            response.generatedBy(),
            response.generatedAt(),
            true
        );
    }

    private boolean samePayload(
        ReportSnapshotResponse existing,
        CreateReportSnapshotCommand command,
        String reportType,
        String dataJson
    ) {

        return Objects.equals(
            existing.campusId(),
            command.campusId()
        )
            && Objects.equals(
                existing.locationId(),
                command.locationId()
            )
            && existing.reportType()
                .equals(
                    reportType
                )
            && samePostgresTimestamp(
                existing.periodStart(),
                command.periodStart()
            )
            && samePostgresTimestamp(
                existing.periodEnd(),
                command.periodEnd()
            )
            && sameJson(
                existing.dataJson(),
                dataJson
            )
            && Objects.equals(
                existing.generatedBy(),
                command.generatedBy()
            );
    }

    private boolean samePostgresTimestamp(
        OffsetDateTime left,
        OffsetDateTime right
    ) {

        if (
            left == null
            || right == null
        ) {
            return left == right;
        }

        Duration difference =
            Duration.between(
                left.toInstant(),
                right.toInstant()
            )
                .abs();

        return difference.compareTo(
            Duration.ofNanos(
                1_000L
            )
        ) < 0;
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

            throw new ReportSnapshotConflictException(
                "Stored report snapshot JSON is invalid."
            );
        }
    }

    private void validateCommand(
        CreateReportSnapshotCommand command
    ) {

        if (command == null) {

            throw new ReportSnapshotValidationException(
                "Report snapshot command is required."
            );
        }

        normalizeReportType(
            command.reportType()
        );

        if (command.periodStart() == null) {

            throw new ReportSnapshotValidationException(
                "Report period start is required."
            );
        }

        if (command.periodEnd() == null) {

            throw new ReportSnapshotValidationException(
                "Report period end is required."
            );
        }

        if (
            !command.periodEnd()
                .isAfter(
                    command.periodStart()
                )
        ) {

            throw new ReportSnapshotValidationException(
                "Report period end must be after period start."
            );
        }

        if (command.dataJson() == null) {

            throw new ReportSnapshotValidationException(
                "Report data is required."
            );
        }
    }

    private String normalizeReportType(
        String reportType
    ) {

        if (reportType == null) {

            throw new ReportSnapshotValidationException(
                "Report type is required."
            );
        }

        String normalized =
            reportType.trim();

        if (normalized.isEmpty()) {

            throw new ReportSnapshotValidationException(
                "Report type is required."
            );
        }

        if (
            normalized.length()
                > REPORT_TYPE_MAX_LENGTH
        ) {

            throw new ReportSnapshotValidationException(
                "Report type exceeds maximum length."
            );
        }

        return normalized;
    }

    private String canonicalRequiredJson(
        String value,
        String label
    ) {

        if (
            value == null
            || value.trim().isEmpty()
        ) {

            throw new ReportSnapshotValidationException(
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

                throw new ReportSnapshotValidationException(
                    label + " must contain valid JSON."
                );
            }

            return canonical;

        } catch (DataAccessException exception) {

            throw new ReportSnapshotValidationException(
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

            throw new ReportSnapshotNotFoundException(
                "Organization does not exist."
            );
        }
    }

    private void lockScope(
        UUID organizationId,
        UUID campusId,
        UUID locationId,
        UUID generatedBy
    ) {

        if (campusId != null) {

            List<UUID> campuses =
                jdbcTemplate.query(
                    """
                    SELECT id
                    FROM campuses
                    WHERE id = ?
                      AND organization_id = ?
                    FOR UPDATE
                    """,
                    (rs, rowNum) ->
                        rs.getObject(
                            "id",
                            UUID.class
                        ),
                    campusId,
                    organizationId
                );

            if (campuses.isEmpty()) {

                throw new ReportSnapshotNotFoundException(
                    "Campus does not exist."
                );
            }
        }

        if (locationId != null) {

            List<UUID> locations;

            if (campusId == null) {

                locations =
                    jdbcTemplate.query(
                        """
                        SELECT l.id
                        FROM locations l
                        JOIN campuses c
                          ON c.id = l.campus_id
                        WHERE l.id = ?
                          AND c.organization_id = ?
                        FOR UPDATE OF l
                        """,
                        (rs, rowNum) ->
                            rs.getObject(
                                "id",
                                UUID.class
                            ),
                        locationId,
                        organizationId
                    );

            } else {

                locations =
                    jdbcTemplate.query(
                        """
                        SELECT l.id
                        FROM locations l
                        JOIN campuses c
                          ON c.id = l.campus_id
                        WHERE l.id = ?
                          AND l.campus_id = ?
                          AND c.organization_id = ?
                        FOR UPDATE OF l
                        """,
                        (rs, rowNum) ->
                            rs.getObject(
                                "id",
                                UUID.class
                            ),
                        locationId,
                        campusId,
                        organizationId
                    );
            }

            if (locations.isEmpty()) {

                throw new ReportSnapshotNotFoundException(
                    "Location does not exist in the report scope."
                );
            }
        }

        if (generatedBy != null) {

            List<UUID> users =
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
                    generatedBy,
                    organizationId
                );

            if (users.isEmpty()) {

                throw new ReportSnapshotNotFoundException(
                    "Report generator does not exist."
                );
            }
        }
    }

    private void requireId(
        UUID id,
        String label
    ) {

        if (id == null) {

            throw new ReportSnapshotValidationException(
                label + " is required."
            );
        }
    }
}