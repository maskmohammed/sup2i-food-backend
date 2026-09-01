package com.sup2i.food.configuration;

import com.sup2i.food.configuration.service.ConfigurationGovernanceService;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.ConflictException;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.DataRetentionAction;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.NotFoundException;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.PreferenceView;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.ResolvedSettingView;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.SettingDefinitionView;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.SettingScopeType;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.SettingValueType;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.SettingView;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.UpsertPreferenceCommand;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.UpsertRetentionCommand;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.UpsertSettingCommand;
import com.sup2i.food.configuration.service.ConfigurationGovernanceService.ValidationException;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
class ConfigurationGovernanceE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_test"
            )
            .withUsername(
                "sup2i_food_test"
            )
            .withPassword(
                "sup2i_food_test"
            );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConfigurationGovernanceService service;

    private TenantSeed primary;
    private TenantSeed other;
    private DefinitionSeed definitions;

    @BeforeEach
    void seedDatabase() {

        primary =
            seedTenant(
                "a"
            );

        other =
            seedTenant(
                "b"
            );

        definitions =
            seedDefinitions();
    }

    @Test
    void definitionsPreserveExactSchemaEnumsAndValueTypes() {

        SettingDefinitionView definition =
            service.getDefinition(
                definitions.organizationJson()
            );

        assertThat(definition.valueType())
            .isEqualTo(
                SettingValueType.JSON
            );

        assertThat(definition.scopeType())
            .isEqualTo(
                SettingScopeType.ORGANIZATION
            );

        assertThatThrownBy(
            () ->
                service.upsertSetting(
                    primary.organizationId(),
                    null,
                    null,
                    definitions.integer(),
                    new UpsertSettingCommand(
                        "1.5",
                        null,
                        primary.userId()
                    )
                )
        )
            .isInstanceOf(
                ValidationException.class
            );
    }

    @Test
    void settingUpsertCanonicalizesReplaysAndAuditsOnce() {

        SettingView created =
            service.upsertSetting(
                primary.organizationId(),
                null,
                null,
                definitions.organizationJson(),
                new UpsertSettingCommand(
                    """
                    {
                      "z": 2,
                      "a": 1
                    }
                    """,
                    null,
                    primary.userId()
                )
            );

        assertThat(
            jsonEquals(
                created.valueJson(),
                "{\"a\":1,\"z\":2}"
            )
        )
            .isTrue();

        SettingView replay =
            service.upsertSetting(
                primary.organizationId(),
                null,
                null,
                definitions.organizationJson(),
                new UpsertSettingCommand(
                    "{\"a\":1,\"z\":2}",
                    null,
                    primary.userId()
                )
            );

        assertThat(replay.id())
            .isEqualTo(
                created.id()
            );

        assertThat(replay.updatedAt())
            .isEqualTo(
                created.updatedAt()
            );

        Long audits =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_logs
                WHERE organization_id = ?
                  AND resource_id = ?
                  AND action =
                      'SYSTEM_SETTING_UPSERT'
                """,
                Long.class,
                primary.organizationId(),
                created.id()
            );

        assertThat(audits)
            .isEqualTo(
                1L
            );
    }

    @Test
    void settingTenantScopeAndAnyPrecedenceAreEnforced() {

        assertThatThrownBy(
            () ->
                service.upsertSetting(
                    primary.organizationId(),
                    other.campusId(),
                    null,
                    definitions.anyJson(),
                    new UpsertSettingCommand(
                        "{}",
                        null,
                        primary.userId()
                    )
                )
        )
            .isInstanceOf(
                NotFoundException.class
            );

        service.upsertSetting(
            primary.organizationId(),
            null,
            null,
            definitions.anyJson(),
            new UpsertSettingCommand(
                "{\"level\":\"organization\"}",
                null,
                primary.userId()
            )
        );

        service.upsertSetting(
            primary.organizationId(),
            primary.campusId(),
            null,
            definitions.anyJson(),
            new UpsertSettingCommand(
                "{\"level\":\"campus\"}",
                null,
                primary.userId()
            )
        );

        service.upsertSetting(
            primary.organizationId(),
            primary.campusId(),
            primary.locationId(),
            definitions.anyJson(),
            new UpsertSettingCommand(
                "{\"level\":\"location\"}",
                null,
                primary.userId()
            )
        );

        ResolvedSettingView location =
            service.resolveSetting(
                primary.organizationId(),
                primary.campusId(),
                primary.locationId(),
                definitions.anyJson()
            );

        assertThat(location.source())
            .isEqualTo(
                "LOCATION"
            );

        service.deleteSetting(
            primary.organizationId(),
            primary.campusId(),
            primary.locationId(),
            definitions.anyJson(),
            primary.userId()
        );

        assertThat(
            service.resolveSetting(
                primary.organizationId(),
                primary.campusId(),
                primary.locationId(),
                definitions.anyJson()
            ).source()
        )
            .isEqualTo(
                "CAMPUS"
            );

        service.deleteSetting(
            primary.organizationId(),
            primary.campusId(),
            null,
            definitions.anyJson(),
            primary.userId()
        );

        assertThat(
            service.resolveSetting(
                primary.organizationId(),
                primary.campusId(),
                primary.locationId(),
                definitions.anyJson()
            ).source()
        )
            .isEqualTo(
                "ORGANIZATION"
            );

        service.deleteSetting(
            primary.organizationId(),
            null,
            null,
            definitions.anyJson(),
            primary.userId()
        );

        assertThat(
            service.resolveSetting(
                primary.organizationId(),
                primary.campusId(),
                primary.locationId(),
                definitions.anyJson()
            ).source()
        )
            .isEqualTo(
                "DEFAULT"
            );
    }

    @Test
    void secretSettingStoresReferenceOnlyAndNeverExposesIt() {

        String secretReference =
            "vault://sup2i/b24/reference";

        SettingView created =
            service.upsertSetting(
                primary.organizationId(),
                primary.campusId(),
                primary.locationId(),
                definitions.secret(),
                new UpsertSettingCommand(
                    null,
                    secretReference,
                    primary.userId()
                )
            );

        assertThat(created.valueJson())
            .isNull();

        assertThat(created.secretConfigured())
            .isTrue();

        assertThat(created.toString())
            .doesNotContain(
                secretReference
            );

        String auditPayload =
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    after_data::text,
                    ''
                )
                FROM audit_logs
                WHERE resource_id = ?
                  AND action =
                      'SYSTEM_SETTING_UPSERT'
                """,
                String.class,
                created.id()
            );

        assertThat(auditPayload)
            .doesNotContain(
                secretReference
            );

        assertThatThrownBy(
            () ->
                service.upsertSetting(
                    primary.organizationId(),
                    primary.campusId(),
                    primary.locationId(),
                    definitions.secret(),
                    new UpsertSettingCommand(
                        "\"raw-secret\"",
                        null,
                        primary.userId()
                    )
                )
        )
            .isInstanceOf(
                ValidationException.class
            );

        assertThatThrownBy(
            () ->
                service.upsertSetting(
                    primary.organizationId(),
                    null,
                    null,
                    definitions.locked(),
                    new UpsertSettingCommand(
                        "\"change\"",
                        null,
                        primary.userId()
                    )
                )
        )
            .isInstanceOf(
                ConflictException.class
            );
    }

    @Test
    void preferencesUseDefaultsCanonicalJsonAndTenantGuards() {

        PreferenceView defaults =
            service.getPreference(
                primary.organizationId(),
                primary.userId()
            );

        assertThat(defaults.locale())
            .isEqualTo(
                "fr"
            );

        assertThat(defaults.timezone())
            .isNull();

        PreferenceView created =
            service.upsertPreference(
                primary.organizationId(),
                primary.userId(),
                new UpsertPreferenceCommand(
                    "fr-MA",
                    "Africa/Casablanca",
                    primary.locationId(),
                    """
                    {
                      "contrast": "high",
                      "keyboard": true
                    }
                    """
                )
            );

        assertThat(created.timezone())
            .isEqualTo(
                "Africa/Casablanca"
            );

        assertThat(created.defaultLocationId())
            .isEqualTo(
                primary.locationId()
            );

        PreferenceView replay =
            service.upsertPreference(
                primary.organizationId(),
                primary.userId(),
                new UpsertPreferenceCommand(
                    "fr-MA",
                    "Africa/Casablanca",
                    primary.locationId(),
                    "{\"keyboard\":true,\"contrast\":\"high\"}"
                )
            );

        assertThat(replay.updatedAt())
            .isEqualTo(
                created.updatedAt()
            );

        assertThatThrownBy(
            () ->
                service.upsertPreference(
                    primary.organizationId(),
                    primary.userId(),
                    new UpsertPreferenceCommand(
                        "fr",
                        "Africa/Casablanca",
                        other.locationId(),
                        "{}"
                    )
                )
        )
            .isInstanceOf(
                NotFoundException.class
            );

        assertThatThrownBy(
            () ->
                service.getPreference(
                    primary.organizationId(),
                    other.userId()
                )
        )
            .isInstanceOf(
                NotFoundException.class
            );
    }

    @Test
    void preferencesRejectInvalidLocaleAndTimezone() {

        assertThatThrownBy(
            () ->
                service.upsertPreference(
                    primary.organizationId(),
                    primary.userId(),
                    new UpsertPreferenceCommand(
                        "bad locale!",
                        "Africa/Casablanca",
                        null,
                        "{}"
                    )
                )
        )
            .isInstanceOf(
                ValidationException.class
            );

        assertThatThrownBy(
            () ->
                service.upsertPreference(
                    primary.organizationId(),
                    primary.userId(),
                    new UpsertPreferenceCommand(
                        "fr",
                        "Mars/Olympus",
                        null,
                        "{}"
                    )
                )
        )
            .isInstanceOf(
                ValidationException.class
            );
    }

    @Test
    void retentionPolicyStorageIsReplaySafeTenantSafeAndAudited() {

        var created =
            service.upsertRetention(
                primary.organizationId(),
                new UpsertRetentionCommand(
                    "REPORT_EXPORT",
                    30,
                    DataRetentionAction.DELETE,
                    "B24 governance E2E",
                    true,
                    primary.userId()
                )
            );

        var replay =
            service.upsertRetention(
                primary.organizationId(),
                new UpsertRetentionCommand(
                    "REPORT_EXPORT",
                    30,
                    DataRetentionAction.DELETE,
                    "B24 governance E2E",
                    true,
                    primary.userId()
                )
            );

        assertThat(replay.id())
            .isEqualTo(
                created.id()
            );

        assertThat(replay.updatedAt())
            .isEqualTo(
                created.updatedAt()
            );

        assertThat(
            service.getRetention(
                primary.organizationId(),
                "REPORT_EXPORT"
            ).action()
        )
            .isEqualTo(
                DataRetentionAction.DELETE
            );

        assertThat(
            service.listRetention(
                primary.organizationId()
            )
        )
            .extracting(
                item ->
                    item.resourceType()
            )
            .contains(
                "REPORT_EXPORT"
            );

        assertThatThrownBy(
            () ->
                service.upsertRetention(
                    primary.organizationId(),
                    new UpsertRetentionCommand(
                        "OTHER",
                        10,
                        DataRetentionAction.ARCHIVE,
                        null,
                        true,
                        other.userId()
                    )
                )
        )
            .isInstanceOf(
                NotFoundException.class
            );

        Long audits =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_logs
                WHERE organization_id = ?
                  AND resource_id = ?
                  AND action =
                      'DATA_RETENTION_POLICY_UPSERT'
                """,
                Long.class,
                primary.organizationId(),
                created.id()
            );

        assertThat(audits)
            .isEqualTo(
                1L
            );
    }

    private boolean jsonEquals(
        String left,
        String right
    ) {

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
    }

    private DefinitionSeed seedDefinitions() {

        String suffix =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                )
                .substring(
                    0,
                    12
                );

        String organizationJson =
            "B24_ORG_JSON_" + suffix;

        String anyJson =
            "B24_ANY_JSON_" + suffix;

        String secret =
            "B24_SECRET_" + suffix;

        String locked =
            "B24_LOCKED_" + suffix;

        String global =
            "B24_GLOBAL_" + suffix;

        String integer =
            "B24_INTEGER_" + suffix;

        insertDefinition(
            organizationJson,
            "JSON",
            "ORGANIZATION",
            "{\"mode\":\"default\"}",
            false,
            true
        );

        insertDefinition(
            anyJson,
            "JSON",
            "ANY",
            "{\"level\":\"default\"}",
            false,
            true
        );

        insertDefinition(
            secret,
            "STRING",
            "ANY",
            null,
            true,
            true
        );

        insertDefinition(
            locked,
            "STRING",
            "ORGANIZATION",
            "\"locked-default\"",
            false,
            false
        );

        insertDefinition(
            global,
            "STRING",
            "GLOBAL",
            "\"global-default\"",
            false,
            true
        );

        insertDefinition(
            integer,
            "INTEGER",
            "ORGANIZATION",
            "1",
            false,
            true
        );

        return new DefinitionSeed(
            organizationJson,
            anyJson,
            secret,
            locked,
            global,
            integer
        );
    }

    private void insertDefinition(
        String settingKey,
        String valueType,
        String scopeType,
        String defaultValue,
        boolean secret,
        boolean editable
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO setting_definitions(
                setting_key,
                value_type,
                scope_type,
                default_value,
                description,
                is_secret,
                is_runtime_editable,
                validation_rules
            )
            VALUES(
                ?, ?, ?,
                CAST(? AS JSONB),
                ?,
                ?,
                ?,
                '{}'::JSONB
            )
            """,
            settingKey,
            valueType,
            scopeType,
            defaultValue,
            "B24 E2E synthetic definition",
            secret,
            editable
        );
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
            jdbcTemplate.queryForObject(
                """
                INSERT INTO organizations(
                    name,
                    code,
                    is_active
                )
                VALUES(
                    ?,
                    ?,
                    TRUE
                )
                RETURNING id
                """,
                UUID.class,
                "B24 Organization " + suffix,
                "B24O" + prefix + suffix
            );

        UUID campusId =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO campuses(
                    organization_id,
                    name,
                    code,
                    timezone,
                    is_active
                )
                VALUES(
                    ?,
                    ?,
                    ?,
                    'Africa/Casablanca',
                    TRUE
                )
                RETURNING id
                """,
                UUID.class,
                organizationId,
                "B24 Campus " + suffix,
                "B24C" + prefix + suffix
            );

        UUID locationId =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO locations(
                    campus_id,
                    name,
                    code,
                    type,
                    is_active
                )
                VALUES(
                    ?,
                    ?,
                    ?,
                    'SNACK',
                    TRUE
                )
                RETURNING id
                """,
                UUID.class,
                campusId,
                "B24 Location " + suffix,
                "B24L" + prefix + suffix
            );

        UUID userId =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO users(
                    organization_id,
                    email,
                    first_name,
                    last_name,
                    status
                )
                VALUES(
                    ?,
                    ?,
                    'B24',
                    'User',
                    'ACTIVE'
                )
                RETURNING id
                """,
                UUID.class,
                organizationId,
                "b24-" + prefix
                    + "-"
                    + suffix
                    + "@sup2i.test"
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

    private record DefinitionSeed(
        String organizationJson,
        String anyJson,
        String secret,
        String locked,
        String global,
        String integer
    ) {
    }
}