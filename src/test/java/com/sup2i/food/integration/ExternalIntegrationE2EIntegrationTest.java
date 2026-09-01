package com.sup2i.food.integration;

import com.sup2i.food.integration.api.dto.CreateImportJobCommand;
import com.sup2i.food.integration.api.dto.CreateIntegrationConnectorCommand;
import com.sup2i.food.integration.api.dto.CreateIntegrationSyncRunCommand;
import com.sup2i.food.integration.api.dto.ExternalEntityRefResponse;
import com.sup2i.food.integration.api.dto.ImportJobResponse;
import com.sup2i.food.integration.api.dto.ImportJobRowResponse;
import com.sup2i.food.integration.api.dto.IntegrationConnectorResponse;
import com.sup2i.food.integration.api.dto.IntegrationInboxEventResponse;
import com.sup2i.food.integration.api.dto.IntegrationSyncItemResponse;
import com.sup2i.food.integration.api.dto.IntegrationSyncRunResponse;
import com.sup2i.food.integration.api.dto.RecordImportJobRowCommand;
import com.sup2i.food.integration.api.dto.RecordIntegrationInboxEventCommand;
import com.sup2i.food.integration.api.dto.RecordIntegrationSyncItemCommand;
import com.sup2i.food.integration.api.dto.RegisterExternalEntityRefCommand;
import com.sup2i.food.integration.domain.ImportJobStatus;
import com.sup2i.food.integration.domain.ImportRowStatus;
import com.sup2i.food.integration.domain.ImportType;
import com.sup2i.food.integration.domain.IntegrationConnectorStatus;
import com.sup2i.food.integration.domain.IntegrationConnectorType;
import com.sup2i.food.integration.domain.IntegrationDirection;
import com.sup2i.food.integration.domain.IntegrationInboxStatus;
import com.sup2i.food.integration.domain.IntegrationSyncItemStatus;
import com.sup2i.food.integration.domain.IntegrationSyncRunStatus;
import com.sup2i.food.integration.exception.IntegrationConflictException;
import com.sup2i.food.integration.exception.IntegrationNotFoundException;
import com.sup2i.food.integration.exception.IntegrationValidationException;
import com.sup2i.food.integration.service.ExternalEntityRefService;
import com.sup2i.food.integration.service.ImportJobService;
import com.sup2i.food.integration.service.IntegrationConnectorService;
import com.sup2i.food.integration.service.IntegrationInboxService;
import com.sup2i.food.integration.service.IntegrationSyncService;

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
class ExternalIntegrationE2EIntegrationTest {

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
    private IntegrationConnectorService connectorService;

    @Autowired
    private ExternalEntityRefService externalRefService;

    @Autowired
    private IntegrationInboxService inboxService;

    @Autowired
    private IntegrationSyncService syncService;

    @Autowired
    private ImportJobService importJobService;

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
    void connectorCreateUsesExactDefaultsCanonicalJsonSecretBoundaryAndReplay() {

        UUID connectorId =
            UUID.randomUUID();

        IntegrationConnectorResponse created =
            connectorService.create(
                primary.organizationId(),
                connectorId,
                new CreateIntegrationConnectorCommand(
                    "  cactus-main  ",
                    IntegrationConnectorType.CACTUS_POS,
                    null,
                    """
                    {
                      "z": 1,
                      "a": true
                    }
                    """,
                    "secret-manager://sup2i/cactus-main"
                )
            );

        assertThat(created.code())
            .isEqualTo(
                "cactus-main"
            );

        assertThat(created.direction())
            .isEqualTo(
                IntegrationDirection.BIDIRECTIONAL
            );

        assertThat(created.status())
            .isEqualTo(
                IntegrationConnectorStatus.DISABLED
            );

        assertThat(created.secretReferenceConfigured())
            .isTrue();

        assertThat(created.replayed())
            .isFalse();

        IntegrationConnectorResponse replay =
            connectorService.create(
                primary.organizationId(),
                connectorId,
                new CreateIntegrationConnectorCommand(
                    "cactus-main",
                    IntegrationConnectorType.CACTUS_POS,
                    IntegrationDirection.BIDIRECTIONAL,
                    "{\"a\":true,\"z\":1}",
                    "secret-manager://sup2i/cactus-main"
                )
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT secret_ref
                FROM integration_connectors
                WHERE id = ?
                """,
                String.class,
                connectorId
            )
        )
            .isEqualTo(
                "secret-manager://sup2i/cactus-main"
            );
    }

    @Test
    void connectorTenantIsolationAndOrgCodeUniquenessAreEnforced() {

        UUID connectorId =
            createConnector(
                primary,
                "same-code",
                IntegrationConnectorType.OTHER
            );

        assertThatThrownBy(
            () ->
                connectorService.get(
                    other.organizationId(),
                    connectorId
                )
        )
            .isInstanceOf(
                IntegrationNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                connectorService.create(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateIntegrationConnectorCommand(
                        "same-code",
                        IntegrationConnectorType.EMAIL,
                        IntegrationDirection.OUTBOUND,
                        "{}",
                        null
                    )
                )
        )
            .isInstanceOf(
                IntegrationConflictException.class
            );

        UUID otherConnector =
            createConnector(
                other,
                "same-code",
                IntegrationConnectorType.OTHER
            );

        assertThat(otherConnector)
            .isNotNull();
    }

    @Test
    void externalEntityReferenceIsTenantSafeCanonicalAndReplayable() {

        UUID connectorId =
            createConnector(
                primary,
                "erp-ref",
                IntegrationConnectorType.STUDENT_ERP
            );

        UUID localEntityId =
            UUID.randomUUID();

        UUID referenceId =
            UUID.randomUUID();

        ExternalEntityRefResponse created =
            externalRefService.register(
                primary.organizationId(),
                referenceId,
                new RegisterExternalEntityRefCommand(
                    connectorId,
                    "STUDENT",
                    localEntityId,
                    "STUDENT",
                    "EXT-42",
                    "v1",
                    "{\"z\":1,\"a\":true}"
                )
            );

        assertThat(created.replayed())
            .isFalse();

        ExternalEntityRefResponse replay =
            externalRefService.register(
                primary.organizationId(),
                referenceId,
                new RegisterExternalEntityRefCommand(
                    connectorId,
                    "STUDENT",
                    localEntityId,
                    "STUDENT",
                    "EXT-42",
                    "v1",
                    "{\"a\":true,\"z\":1}"
                )
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThatThrownBy(
            () ->
                externalRefService.get(
                    other.organizationId(),
                    referenceId
                )
        )
            .isInstanceOf(
                IntegrationNotFoundException.class
            );
    }

    @Test
    void inboxExternalEventNaturalDedupeIsSemanticAndTenantSafe() {

        UUID connectorId =
            createConnector(
                primary,
                "inbox",
                IntegrationConnectorType.OTHER
            );

        UUID firstId =
            UUID.randomUUID();

        IntegrationInboxEventResponse created =
            inboxService.record(
                primary.organizationId(),
                firstId,
                new RecordIntegrationInboxEventCommand(
                    connectorId,
                    "EVENT-42",
                    "student.changed",
                    "{\"z\":1,\"a\":true}"
                )
            );

        assertThat(created.status())
            .isEqualTo(
                IntegrationInboxStatus.RECEIVED
            );

        assertThat(created.retryCount())
            .isZero();

        IntegrationInboxEventResponse replay =
            inboxService.record(
                primary.organizationId(),
                UUID.randomUUID(),
                new RecordIntegrationInboxEventCommand(
                    connectorId,
                    "EVENT-42",
                    "student.changed",
                    "{\"a\":true,\"z\":1}"
                )
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThat(replay.id())
            .isEqualTo(
                firstId
            );

        assertThatThrownBy(
            () ->
                inboxService.record(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new RecordIntegrationInboxEventCommand(
                        connectorId,
                        "EVENT-42",
                        "student.changed",
                        "{\"a\":false,\"z\":1}"
                    )
                )
        )
            .isInstanceOf(
                IntegrationConflictException.class
            );

        assertThatThrownBy(
            () ->
                inboxService.get(
                    other.organizationId(),
                    firstId
                )
        )
            .isInstanceOf(
                IntegrationNotFoundException.class
            );
    }

    @Test
    void syncRunStartsRunningWithZeroCountersAndReplaySurvivesProgression() {

        UUID connectorId =
            createConnector(
                primary,
                "sync-run",
                IntegrationConnectorType.STUDENT_ERP
            );

        UUID runId =
            UUID.randomUUID();

        CreateIntegrationSyncRunCommand command =
            new CreateIntegrationSyncRunCommand(
                connectorId,
                "  STUDENT_REFRESH  ",
                primary.userId()
            );

        IntegrationSyncRunResponse created =
            syncService.createRun(
                primary.organizationId(),
                runId,
                command
            );

        assertThat(created.status())
            .isEqualTo(
                IntegrationSyncRunStatus.RUNNING
            );

        assertThat(created.processedCount())
            .isZero();

        assertThat(created.successCount())
            .isZero();

        assertThat(created.failureCount())
            .isZero();

        int updated =
            jdbcTemplate.update(
                """
                UPDATE integration_sync_runs
                SET status = 'COMPLETED',
                    completed_at = CURRENT_TIMESTAMP,
                    processed_count = 4,
                    success_count = 3,
                    failure_count = 1
                WHERE id = ?
                """,
                runId
            );

        assertThat(updated)
            .isEqualTo(
                1
            );

        IntegrationSyncRunResponse replay =
            syncService.createRun(
                primary.organizationId(),
                runId,
                new CreateIntegrationSyncRunCommand(
                    connectorId,
                    "STUDENT_REFRESH",
                    primary.userId()
                )
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThat(replay.status())
            .isEqualTo(
                IntegrationSyncRunStatus.COMPLETED
            );

        assertThat(replay.processedCount())
            .isEqualTo(
                4
            );
    }

    @Test
    void syncRunRejectsCrossTenantConnectorAndInitiator() {

        UUID primaryConnector =
            createConnector(
                primary,
                "sync-tenant",
                IntegrationConnectorType.OTHER
            );

        UUID otherConnector =
            createConnector(
                other,
                "sync-tenant",
                IntegrationConnectorType.OTHER
            );

        assertThatThrownBy(
            () ->
                syncService.createRun(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateIntegrationSyncRunCommand(
                        otherConnector,
                        "SYNC",
                        primary.userId()
                    )
                )
        )
            .isInstanceOf(
                IntegrationNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                syncService.createRun(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateIntegrationSyncRunCommand(
                        primaryConnector,
                        "SYNC",
                        other.userId()
                    )
                )
        )
            .isInstanceOf(
                IntegrationNotFoundException.class
            );
    }

    @Test
    void syncItemSupportsExactStatusFreeTaxonomiesCanonicalJsonAndNoCounterMutation() {

        UUID connectorId =
            createConnector(
                primary,
                "sync-item",
                IntegrationConnectorType.OTHER
            );

        UUID runId =
            createRun(
                primary,
                connectorId,
                "CUSTOM_SYNC"
            );

        UUID itemId =
            UUID.randomUUID();

        IntegrationSyncItemResponse created =
            syncService.recordItem(
                primary.organizationId(),
                itemId,
                new RecordIntegrationSyncItemCommand(
                    runId,
                    "CUSTOM_ENTITY",
                    "EXT-99",
                    UUID.randomUUID(),
                    IntegrationSyncItemStatus.CONFLICT,
                    "CUSTOM_ACTION",
                    "CONFLICT_1",
                    "Needs review",
                    "{\"z\":1,\"a\":true}"
                )
            );

        assertThat(created.status())
            .isEqualTo(
                IntegrationSyncItemStatus.CONFLICT
            );

        assertThat(created.entityType())
            .isEqualTo(
                "CUSTOM_ENTITY"
            );

        assertThat(created.action())
            .isEqualTo(
                "CUSTOM_ACTION"
            );

        IntegrationSyncItemResponse replay =
            syncService.recordItem(
                primary.organizationId(),
                itemId,
                new RecordIntegrationSyncItemCommand(
                    runId,
                    "CUSTOM_ENTITY",
                    "EXT-99",
                    created.localEntityId(),
                    IntegrationSyncItemStatus.CONFLICT,
                    "CUSTOM_ACTION",
                    "CONFLICT_1",
                    "Needs review",
                    "{\"a\":true,\"z\":1}"
                )
            );

        assertThat(replay.replayed())
            .isTrue();

        Integer processed =
            jdbcTemplate.queryForObject(
                """
                SELECT processed_count
                FROM integration_sync_runs
                WHERE id = ?
                """,
                Integer.class,
                runId
            );

        assertThat(processed)
            .isZero();

        List<IntegrationSyncItemResponse> rows =
            syncService.listItems(
                primary.organizationId(),
                runId
            );

        assertThat(rows)
            .extracting(
                IntegrationSyncItemResponse::id
            )
            .containsExactly(
                itemId
            );
    }

    @Test
    void syncItemCannotCrossTenantRunBoundary() {

        UUID otherConnector =
            createConnector(
                other,
                "other-run",
                IntegrationConnectorType.OTHER
            );

        UUID otherRun =
            createRun(
                other,
                otherConnector,
                "SYNC"
            );

        assertThatThrownBy(
            () ->
                syncService.recordItem(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new RecordIntegrationSyncItemCommand(
                        otherRun,
                        null,
                        null,
                        null,
                        IntegrationSyncItemStatus.SUCCESS,
                        null,
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                IntegrationNotFoundException.class
            );
    }

    @Test
    void importJobStartsPendingZeroAndReplaySurvivesProgression() {

        UUID jobId =
            UUID.randomUUID();

        CreateImportJobCommand command =
            new CreateImportJobCommand(
                ImportType.STUDENTS,
                primary.fileAssetId(),
                primary.userId()
            );

        ImportJobResponse created =
            importJobService.createJob(
                primary.organizationId(),
                jobId,
                command
            );

        assertThat(created.status())
            .isEqualTo(
                ImportJobStatus.PENDING
            );

        assertThat(created.totalRows())
            .isZero();

        assertThat(created.successRows())
            .isZero();

        assertThat(created.failedRows())
            .isZero();

        int updated =
            jdbcTemplate.update(
                """
                UPDATE import_jobs
                SET status = 'COMPLETED',
                    started_at = CURRENT_TIMESTAMP,
                    completed_at = CURRENT_TIMESTAMP,
                    total_rows = 3,
                    success_rows = 2,
                    failed_rows = 1
                WHERE id = ?
                """,
                jobId
            );

        assertThat(updated)
            .isEqualTo(
                1
            );

        ImportJobResponse replay =
            importJobService.createJob(
                primary.organizationId(),
                jobId,
                command
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThat(replay.status())
            .isEqualTo(
                ImportJobStatus.COMPLETED
            );

        assertThat(replay.totalRows())
            .isEqualTo(
                3
            );
    }

    @Test
    void importJobGuardsRequesterAndSourceFileTenant() {

        assertThatThrownBy(
            () ->
                importJobService.createJob(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateImportJobCommand(
                        ImportType.PRODUCTS,
                        primary.fileAssetId(),
                        other.userId()
                    )
                )
        )
            .isInstanceOf(
                IntegrationNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                importJobService.createJob(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateImportJobCommand(
                        ImportType.STOCK,
                        other.fileAssetId(),
                        primary.userId()
                    )
                )
        )
            .isInstanceOf(
                IntegrationNotFoundException.class
            );
    }

    @Test
    void importRowNaturalDedupeUsesSemanticJsonAndReplaySurvivesProgression() {

        UUID jobId =
            createImportJob(
                primary,
                ImportType.PRODUCTS
            );

        UUID rowId =
            UUID.randomUUID();

        ImportJobRowResponse created =
            importJobService.recordRow(
                primary.organizationId(),
                rowId,
                new RecordImportJobRowCommand(
                    jobId,
                    1,
                    "{\"z\":1,\"a\":true}"
                )
            );

        assertThat(created.status())
            .isEqualTo(
                ImportRowStatus.PENDING
            );

        int updated =
            jdbcTemplate.update(
                """
                UPDATE import_job_rows
                SET status = 'SUCCESS',
                    local_entity_id = ?,
                    processed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                UUID.randomUUID(),
                rowId
            );

        assertThat(updated)
            .isEqualTo(
                1
            );

        ImportJobRowResponse replay =
            importJobService.recordRow(
                primary.organizationId(),
                UUID.randomUUID(),
                new RecordImportJobRowCommand(
                    jobId,
                    1,
                    "{\"a\":true,\"z\":1}"
                )
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThat(replay.id())
            .isEqualTo(
                rowId
            );

        assertThat(replay.status())
            .isEqualTo(
                ImportRowStatus.SUCCESS
            );

        assertThatThrownBy(
            () ->
                importJobService.recordRow(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new RecordImportJobRowCommand(
                        jobId,
                        1,
                        "{\"a\":false,\"z\":1}"
                    )
                )
        )
            .isInstanceOf(
                IntegrationConflictException.class
            );

        Integer totalRows =
            jdbcTemplate.queryForObject(
                """
                SELECT total_rows
                FROM import_jobs
                WHERE id = ?
                """,
                Integer.class,
                jobId
            );

        assertThat(totalRows)
            .isZero();
    }

    @Test
    void syncAndImportValidationRejectsInventedOrInvalidPayloadShapes() {

        UUID connectorId =
            createConnector(
                primary,
                "validation",
                IntegrationConnectorType.OTHER
            );

        assertThatThrownBy(
            () ->
                syncService.createRun(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateIntegrationSyncRunCommand(
                        connectorId,
                        "   ",
                        primary.userId()
                    )
                )
        )
            .isInstanceOf(
                IntegrationValidationException.class
            );

        UUID runId =
            createRun(
                primary,
                connectorId,
                "VALID"
            );

        assertThatThrownBy(
            () ->
                syncService.recordItem(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new RecordIntegrationSyncItemCommand(
                        runId,
                        null,
                        null,
                        null,
                        IntegrationSyncItemStatus.SUCCESS,
                        null,
                        null,
                        null,
                        "{broken"
                    )
                )
        )
            .isInstanceOf(
                IntegrationValidationException.class
            );

        UUID jobId =
            createImportJob(
                primary,
                ImportType.OTHER
            );

        assertThatThrownBy(
            () ->
                importJobService.recordRow(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new RecordImportJobRowCommand(
                        jobId,
                        0,
                        "{}"
                    )
                )
        )
            .isInstanceOf(
                IntegrationValidationException.class
            );

        assertThatThrownBy(
            () ->
                importJobService.recordRow(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new RecordImportJobRowCommand(
                        jobId,
                        2,
                        "{broken"
                    )
                )
        )
            .isInstanceOf(
                IntegrationValidationException.class
            );
    }

    private UUID createConnector(
        TenantSeed tenant,
        String code,
        IntegrationConnectorType type
    ) {

        UUID connectorId =
            UUID.randomUUID();

        connectorService.create(
            tenant.organizationId(),
            connectorId,
            new CreateIntegrationConnectorCommand(
                code,
                type,
                IntegrationDirection.BIDIRECTIONAL,
                "{}",
                null
            )
        );

        return connectorId;
    }

    private UUID createRun(
        TenantSeed tenant,
        UUID connectorId,
        String syncType
    ) {

        UUID runId =
            UUID.randomUUID();

        syncService.createRun(
            tenant.organizationId(),
            runId,
            new CreateIntegrationSyncRunCommand(
                connectorId,
                syncType,
                tenant.userId()
            )
        );

        return runId;
    }

    private UUID createImportJob(
        TenantSeed tenant,
        ImportType importType
    ) {

        UUID jobId =
            UUID.randomUUID();

        importJobService.createJob(
            tenant.organizationId(),
            jobId,
            new CreateImportJobCommand(
                importType,
                tenant.fileAssetId(),
                tenant.userId()
            )
        );

        return jobId;
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

        UUID userId =
            UUID.randomUUID();

        UUID fileAssetId =
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
            "B22 Organization " + suffix,
            "B22O" + prefix + suffix
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
            "b22-" + prefix + "-" + suffix + "@sup2i.test",
            "B22",
            "User"
        );

        jdbcTemplate.update(
            """
            INSERT INTO file_assets(
                id,
                organization_id,
                storage_key,
                original_name,
                mime_type,
                size_bytes,
                created_by
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            fileAssetId,
            organizationId,
            "b22/" + prefix + "/" + suffix + ".csv",
            "b22-" + suffix + ".csv",
            "text/csv",
            128L,
            userId
        );

        return new TenantSeed(
            organizationId,
            userId,
            fileAssetId
        );
    }

    private record TenantSeed(
        UUID organizationId,
        UUID userId,
        UUID fileAssetId
    ) {
    }
}