package com.sup2i.food.reporting;

import com.sup2i.food.reporting.api.dto.CreateReportSnapshotCommand;
import com.sup2i.food.reporting.api.dto.ReportSnapshotResponse;
import com.sup2i.food.reporting.exception.ReportSnapshotConflictException;
import com.sup2i.food.reporting.exception.ReportSnapshotNotFoundException;
import com.sup2i.food.reporting.exception.ReportSnapshotValidationException;
import com.sup2i.food.reporting.export.api.dto.CreateReportExportCommand;
import com.sup2i.food.reporting.export.api.dto.ReportExportResponse;
import com.sup2i.food.reporting.export.domain.ReportExportStatus;
import com.sup2i.food.reporting.export.domain.ReportExportType;
import com.sup2i.food.reporting.export.exception.ReportExportConflictException;
import com.sup2i.food.reporting.export.exception.ReportExportNotFoundException;
import com.sup2i.food.reporting.export.service.ReportExportService;
import com.sup2i.food.reporting.kpi.api.dto.DocumentedKpiInput;
import com.sup2i.food.reporting.kpi.api.dto.DocumentedKpiResponse;
import com.sup2i.food.reporting.kpi.exception.ReportingKpiValidationException;
import com.sup2i.food.reporting.kpi.service.DocumentedKpiService;
import com.sup2i.food.reporting.service.ReportSnapshotService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
@Testcontainers
class ReportingE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_test"
            );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReportSnapshotService snapshotService;

    @Autowired
    private ReportExportService exportService;

    @Autowired
    private DocumentedKpiService kpiService;

    private TenantSeed primary;
    private TenantSeed other;

    @BeforeEach
    void seedTenants() {

        primary =
            seedTenant(
                "a"
            );

        other =
            seedTenant(
                "b"
            );
    }

    @Test
    void snapshotCreateReplayCanonicalJsonAndTimestampPrecision() {

        UUID snapshotId =
            UUID.randomUUID();

        OffsetDateTime periodStart =
            OffsetDateTime.now()
                .minusHours(
                    2
                )
                .withNano(
                    123456789
                );

        OffsetDateTime periodEnd =
            periodStart.plusHours(
                1
            );

        CreateReportSnapshotCommand command =
            new CreateReportSnapshotCommand(
                primary.campusId(),
                primary.locationId(),
                "  DAILY_EXECUTIVE  ",
                periodStart,
                periodEnd,
                """
                {
                  "z": 1,
                  "a": true
                }
                """,
                primary.userId()
            );

        ReportSnapshotResponse created =
            snapshotService.create(
                primary.organizationId(),
                snapshotId,
                command
            );

        assertThat(created.replayed())
            .isFalse();

        assertThat(created.reportType())
            .isEqualTo(
                "DAILY_EXECUTIVE"
            );

        ReportSnapshotResponse replay =
            snapshotService.create(
                primary.organizationId(),
                snapshotId,
                new CreateReportSnapshotCommand(
                    primary.campusId(),
                    primary.locationId(),
                    "DAILY_EXECUTIVE",
                    periodStart,
                    periodEnd,
                    "{\"a\":true,\"z\":1}",
                    primary.userId()
                )
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThat(
            snapshotService.get(
                primary.organizationId(),
                snapshotId
            ).id()
        )
            .isEqualTo(
                snapshotId
            );
    }

    @Test
    void snapshotSameIdDifferentPayloadConflicts() {

        UUID snapshotId =
            UUID.randomUUID();

        OffsetDateTime start =
            OffsetDateTime.now()
                .minusHours(
                    2
                );

        OffsetDateTime end =
            start.plusHours(
                1
            );

        snapshotService.create(
            primary.organizationId(),
            snapshotId,
            new CreateReportSnapshotCommand(
                primary.campusId(),
                primary.locationId(),
                "B21_CONFLICT",
                start,
                end,
                "{\"value\":1}",
                primary.userId()
            )
        );

        assertThatThrownBy(
            () ->
                snapshotService.create(
                    primary.organizationId(),
                    snapshotId,
                    new CreateReportSnapshotCommand(
                        primary.campusId(),
                        primary.locationId(),
                        "B21_CONFLICT",
                        start,
                        end,
                        "{\"value\":2}",
                        primary.userId()
                    )
                )
        )
            .isInstanceOf(
                ReportSnapshotConflictException.class
            );
    }

    @Test
    void snapshotScopeIsTenantSafeAndCampusLocationCoherent() {

        OffsetDateTime start =
            OffsetDateTime.now()
                .minusHours(
                    2
                );

        OffsetDateTime end =
            start.plusHours(
                1
            );

        assertThatThrownBy(
            () ->
                snapshotService.create(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateReportSnapshotCommand(
                        other.campusId(),
                        null,
                        "B21_SCOPE",
                        start,
                        end,
                        "{}",
                        primary.userId()
                    )
                )
        )
            .isInstanceOf(
                ReportSnapshotNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                snapshotService.create(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateReportSnapshotCommand(
                        primary.campusId(),
                        other.locationId(),
                        "B21_SCOPE",
                        start,
                        end,
                        "{}",
                        primary.userId()
                    )
                )
        )
            .isInstanceOf(
                ReportSnapshotNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                snapshotService.create(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateReportSnapshotCommand(
                        primary.campusId(),
                        primary.locationId(),
                        "B21_SCOPE",
                        start,
                        end,
                        "{}",
                        other.userId()
                    )
                )
        )
            .isInstanceOf(
                ReportSnapshotNotFoundException.class
            );
    }

    @Test
    void snapshotValidationRejectsInvalidPeriodTypeAndJson() {

        OffsetDateTime now =
            OffsetDateTime.now();

        assertThatThrownBy(
            () ->
                snapshotService.create(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateReportSnapshotCommand(
                        null,
                        null,
                        "B21_PERIOD",
                        now,
                        now,
                        "{}",
                        null
                    )
                )
        )
            .isInstanceOf(
                ReportSnapshotValidationException.class
            );

        assertThatThrownBy(
            () ->
                snapshotService.create(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateReportSnapshotCommand(
                        null,
                        null,
                        "   ",
                        now.minusHours(
                            1
                        ),
                        now,
                        "{}",
                        null
                    )
                )
        )
            .isInstanceOf(
                ReportSnapshotValidationException.class
            );

        assertThatThrownBy(
            () ->
                snapshotService.create(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateReportSnapshotCommand(
                        null,
                        null,
                        "B21_JSON",
                        now.minusHours(
                            1
                        ),
                        now,
                        "{broken",
                        null
                    )
                )
        )
            .isInstanceOf(
                ReportSnapshotValidationException.class
            );
    }

    @Test
    void snapshotListIsTenantIsolatedAndReportTypeRemainsFree() {

        String reportType =
            "CUSTOM_FINANCE_"
                + UUID.randomUUID()
                    .toString()
                    .substring(
                        0,
                        8
                    );

        UUID primaryId =
            createSnapshot(
                primary,
                reportType
            );

        createSnapshot(
            other,
            reportType
        );

        List<ReportSnapshotResponse> primaryRows =
            snapshotService.listForType(
                primary.organizationId(),
                reportType
            );

        assertThat(primaryRows)
            .extracting(
                ReportSnapshotResponse::id
            )
            .containsExactly(
                primaryId
            );

        assertThatThrownBy(
            () ->
                snapshotService.get(
                    other.organizationId(),
                    primaryId
                )
        )
            .isInstanceOf(
                ReportSnapshotNotFoundException.class
            );
    }

    @Test
    void exportRequestSupportsExactTypesPendingAndSemanticReplay() {

        UUID snapshotId =
            createSnapshot(
                primary,
                "B21_EXPORT"
            );

        UUID csvId =
            UUID.randomUUID();

        CreateReportExportCommand csvCommand =
            new CreateReportExportCommand(
                snapshotId,
                ReportExportType.CSV,
                primary.userId(),
                """
                {
                  "z": 1,
                  "a": true
                }
                """
            );

        ReportExportResponse csv =
            exportService.request(
                primary.organizationId(),
                csvId,
                csvCommand
            );

        ReportExportResponse xlsx =
            exportService.request(
                primary.organizationId(),
                UUID.randomUUID(),
                new CreateReportExportCommand(
                    snapshotId,
                    ReportExportType.XLSX,
                    primary.userId(),
                    null
                )
            );

        ReportExportResponse pdf =
            exportService.request(
                primary.organizationId(),
                UUID.randomUUID(),
                new CreateReportExportCommand(
                    snapshotId,
                    ReportExportType.PDF,
                    primary.userId(),
                    null
                )
            );

        assertThat(csv.status())
            .isEqualTo(
                ReportExportStatus.PENDING
            );

        assertThat(xlsx.status())
            .isEqualTo(
                ReportExportStatus.PENDING
            );

        assertThat(pdf.status())
            .isEqualTo(
                ReportExportStatus.PENDING
            );

        assertThat(csv.fileAssetId())
            .isNull();

        assertThat(csv.completedAt())
            .isNull();

        ReportExportResponse replay =
            exportService.request(
                primary.organizationId(),
                csvId,
                new CreateReportExportCommand(
                    snapshotId,
                    ReportExportType.CSV,
                    primary.userId(),
                    "{\"a\":true,\"z\":1}"
                )
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM report_exports
                WHERE organization_id = ?
                """,
                Long.class,
                primary.organizationId()
            )
        )
            .isEqualTo(
                3L
            );
    }

    @Test
    void exportSameIdDifferentPayloadConflicts() {

        UUID snapshotId =
            createSnapshot(
                primary,
                "B21_EXPORT_CONFLICT"
            );

        UUID exportId =
            UUID.randomUUID();

        exportService.request(
            primary.organizationId(),
            exportId,
            new CreateReportExportCommand(
                snapshotId,
                ReportExportType.CSV,
                primary.userId(),
                "{\"period\":\"week\"}"
            )
        );

        assertThatThrownBy(
            () ->
                exportService.request(
                    primary.organizationId(),
                    exportId,
                    new CreateReportExportCommand(
                        snapshotId,
                        ReportExportType.PDF,
                        primary.userId(),
                        "{\"period\":\"week\"}"
                    )
                )
        )
            .isInstanceOf(
                ReportExportConflictException.class
            );
    }

    @Test
    void exportTenantGuardsRequesterSnapshotAndRead() {

        UUID primarySnapshot =
            createSnapshot(
                primary,
                "B21_EXPORT_TENANT"
            );

        UUID otherSnapshot =
            createSnapshot(
                other,
                "B21_EXPORT_TENANT"
            );

        assertThatThrownBy(
            () ->
                exportService.request(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateReportExportCommand(
                        primarySnapshot,
                        ReportExportType.CSV,
                        other.userId(),
                        null
                    )
                )
        )
            .isInstanceOf(
                ReportExportNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                exportService.request(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateReportExportCommand(
                        otherSnapshot,
                        ReportExportType.CSV,
                        primary.userId(),
                        null
                    )
                )
        )
            .isInstanceOf(
                ReportExportNotFoundException.class
            );

        UUID exportId =
            UUID.randomUUID();

        exportService.request(
            primary.organizationId(),
            exportId,
            new CreateReportExportCommand(
                primarySnapshot,
                ReportExportType.CSV,
                primary.userId(),
                null
            )
        );

        assertThatThrownBy(
            () ->
                exportService.get(
                    other.organizationId(),
                    exportId
                )
        )
            .isInstanceOf(
                ReportExportNotFoundException.class
            );
    }

    @Test
    void exportReplayRemainsValidAfterStatusProgression() {

        UUID snapshotId =
            createSnapshot(
                primary,
                "B21_REPLAY_STATUS"
            );

        UUID exportId =
            UUID.randomUUID();

        CreateReportExportCommand command =
            new CreateReportExportCommand(
                snapshotId,
                ReportExportType.PDF,
                primary.userId(),
                "{\"period\":\"month\"}"
            );

        exportService.request(
            primary.organizationId(),
            exportId,
            command
        );

        int updated =
            jdbcTemplate.update(
                """
                UPDATE report_exports
                SET status = 'COMPLETED',
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                exportId
            );

        assertThat(updated)
            .isEqualTo(
                1
            );

        ReportExportResponse replay =
            exportService.request(
                primary.organizationId(),
                exportId,
                command
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThat(replay.status())
            .isEqualTo(
                ReportExportStatus.COMPLETED
            );

        assertThat(replay.completedAt())
            .isNotNull();

        assertThat(
            exportService.listByStatus(
                primary.organizationId(),
                ReportExportStatus.COMPLETED
            )
        )
            .extracting(
                ReportExportResponse::id
            )
            .contains(
                exportId
            );
    }

    @Test
    void documentedKpiFormulasMatchMasterAndUndefinedRatiosStayNull() {

        DocumentedKpiResponse response =
            kpiService.calculate(
                new DocumentedKpiInput(
                    new BigDecimal(
                        "100.00"
                    ),
                    4L,
                    3L,
                    4L,
                    9L,
                    12L,
                    new BigDecimal(
                        "2.00"
                    ),
                    new BigDecimal(
                        "10.00"
                    ),
                    new BigDecimal(
                        "3.00"
                    ),
                    new BigDecimal(
                        "12.00"
                    ),
                    new BigDecimal(
                        "100.00"
                    )
                )
            );

        assertThat(response.averageBasket())
            .isEqualByComparingTo(
                new BigDecimal(
                    "25"
                )
            );

        assertThat(response.preorderRate())
            .isEqualByComparingTo(
                new BigDecimal(
                    "0.75"
                )
            );

        assertThat(response.canteenUsageRate())
            .isEqualByComparingTo(
                new BigDecimal(
                    "0.75"
                )
            );

        assertThat(response.wasteQuantityRate())
            .isEqualByComparingTo(
                new BigDecimal(
                    "0.2"
                )
            );

        assertThat(response.wasteValueRate())
            .isEqualByComparingTo(
                new BigDecimal(
                    "0.25"
                )
            );

        assertThat(response.estimatedGrossMaterialMargin())
            .isEqualByComparingTo(
                new BigDecimal(
                    "88"
                )
            );

        DocumentedKpiResponse undefined =
            kpiService.calculate(
                new DocumentedKpiInput(
                    BigDecimal.ZERO,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
                )
            );

        assertThat(undefined.averageBasket())
            .isNull();

        assertThat(undefined.preorderRate())
            .isNull();

        assertThat(undefined.canteenUsageRate())
            .isNull();

        assertThat(undefined.wasteQuantityRate())
            .isNull();

        assertThat(undefined.wasteValueRate())
            .isNull();

        assertThat(undefined.estimatedGrossMaterialMargin())
            .isEqualByComparingTo(
                BigDecimal.ZERO
            );

        assertThatThrownBy(
            () ->
                kpiService.calculate(
                    new DocumentedKpiInput(
                        BigDecimal.ZERO,
                        -1L,
                        0L,
                        0L,
                        0L,
                        0L,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                    )
                )
        )
            .isInstanceOf(
                ReportingKpiValidationException.class
            );
    }

    private UUID createSnapshot(
        TenantSeed tenant,
        String reportType
    ) {

        OffsetDateTime start =
            OffsetDateTime.now()
                .minusHours(
                    2
                );

        UUID snapshotId =
            UUID.randomUUID();

        snapshotService.create(
            tenant.organizationId(),
            snapshotId,
            new CreateReportSnapshotCommand(
                tenant.campusId(),
                tenant.locationId(),
                reportType,
                start,
                start.plusHours(
                    1
                ),
                "{\"source\":\"b21-e2e\"}",
                tenant.userId()
            )
        );

        return snapshotId;
    }

    private TenantSeed seedTenant(
        String prefix
    ) {

        String suffix =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                )
                .substring(
                    0,
                    10
                );

        UUID organizationId =
            UUID.randomUUID();

        UUID campusId =
            UUID.randomUUID();

        UUID locationId =
            UUID.randomUUID();

        UUID userId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO organizations(
                id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            organizationId,
            "B21 Organization " + suffix,
            "B21O" + prefix + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO campuses(
                id,
                organization_id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, ?, TRUE)
            """,
            campusId,
            organizationId,
            "B21 Campus " + suffix,
            "B21C" + prefix + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO locations(
                id,
                campus_id,
                name,
                code,
                type,
                is_active
            )
            VALUES (?, ?, ?, ?, 'SNACK', TRUE)
            """,
            locationId,
            campusId,
            "B21 Location " + suffix,
            "B21L" + prefix + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO users(
                id,
                organization_id,
                email,
                first_name,
                last_name,
                status
            )
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """,
            userId,
            organizationId,
            "b21-" + prefix + "-" + suffix + "@sup2i.test",
            "B21",
            "User"
        );

        return new TenantSeed(
            organizationId,
            campusId,
            locationId,
            userId
        );
    }

    private record TenantSeed(
        UUID organizationId,
        UUID campusId,
        UUID locationId,
        UUID userId
    ) {
    }
}