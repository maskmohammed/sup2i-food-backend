package com.sup2i.food.configuration.service;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ConfigurationGovernanceService {

    private static final int SETTING_KEY_MAX_LENGTH = 150;
    private static final int SECRET_REF_MAX_LENGTH = 500;
    private static final int RESOURCE_TYPE_MAX_LENGTH = 80;
    private static final int LOCALE_MAX_LENGTH = 20;
    private static final int TIMEZONE_MAX_LENGTH = 80;

    private static final Pattern LOCALE_PATTERN =
        Pattern.compile(
            "^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8})?$"
        );

    private final JdbcTemplate jdbcTemplate;

    public ConfigurationGovernanceService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public enum SettingValueType {
        STRING,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        DURATION,
        JSON
    }

    public enum SettingScopeType {
        GLOBAL,
        ORGANIZATION,
        CAMPUS,
        LOCATION,
        ANY
    }

    public enum DataRetentionAction {
        KEEP,
        ARCHIVE,
        ANONYMIZE,
        DELETE
    }

    public static class ValidationException
        extends RuntimeException {

        public ValidationException(
            String message
        ) {
            super(message);
        }
    }

    public static class NotFoundException
        extends RuntimeException {

        public NotFoundException(
            String message
        ) {
            super(message);
        }
    }

    public static class ConflictException
        extends RuntimeException {

        public ConflictException(
            String message
        ) {
            super(message);
        }
    }

    public record SettingDefinitionView(
        String settingKey,
        SettingValueType valueType,
        SettingScopeType scopeType,
        String defaultValueJson,
        String description,
        boolean secret,
        boolean runtimeEditable,
        String validationRulesJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record UpsertSettingCommand(
        String valueJson,
        String secretRef,
        UUID updatedBy
    ) {
    }

    public record SettingView(
        UUID id,
        UUID organizationId,
        UUID campusId,
        UUID locationId,
        String settingKey,
        String valueJson,
        boolean secretConfigured,
        UUID updatedBy,
        OffsetDateTime updatedAt
    ) {
    }

    public record ResolvedSettingView(
        String settingKey,
        String valueJson,
        boolean secretConfigured,
        String source,
        UUID campusId,
        UUID locationId
    ) {
    }

    public record UpsertPreferenceCommand(
        String locale,
        String timezone,
        UUID defaultLocationId,
        String accessibilityJson
    ) {
    }

    public record PreferenceView(
        UUID userId,
        String locale,
        String timezone,
        UUID defaultLocationId,
        String accessibilityJson,
        OffsetDateTime updatedAt
    ) {
    }

    public record UpsertRetentionCommand(
        String resourceType,
        Integer retentionDays,
        DataRetentionAction action,
        String legalBasisNote,
        boolean active,
        UUID updatedBy
    ) {
    }

    public record RetentionView(
        UUID id,
        UUID organizationId,
        String resourceType,
        Integer retentionDays,
        DataRetentionAction action,
        String legalBasisNote,
        boolean active,
        UUID updatedBy,
        OffsetDateTime updatedAt
    ) {
    }

    private record DefinitionRow(
        String settingKey,
        SettingValueType valueType,
        SettingScopeType scopeType,
        String defaultValueJson,
        String description,
        boolean secret,
        boolean runtimeEditable,
        String validationRulesJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    private record SettingRow(
        UUID id,
        UUID organizationId,
        UUID campusId,
        UUID locationId,
        String settingKey,
        String valueJson,
        String secretRef,
        UUID updatedBy,
        OffsetDateTime updatedAt
    ) {
    }

    private record PreferenceRow(
        UUID userId,
        String locale,
        String timezone,
        UUID defaultLocationId,
        String accessibilityJson,
        OffsetDateTime updatedAt
    ) {
    }

    private record RetentionRow(
        UUID id,
        UUID organizationId,
        String resourceType,
        Integer retentionDays,
        DataRetentionAction action,
        String legalBasisNote,
        boolean active,
        UUID updatedBy,
        OffsetDateTime updatedAt
    ) {
    }

    @Transactional(readOnly = true)
    public List<SettingDefinitionView> listDefinitions() {

        return jdbcTemplate.query(
            """
            SELECT
                setting_key,
                value_type,
                scope_type,
                default_value::text AS default_value_json,
                description,
                is_secret,
                is_runtime_editable,
                validation_rules::text AS validation_rules_json,
                created_at,
                updated_at
            FROM setting_definitions
            ORDER BY setting_key
            """,
            (
                resultSet,
                rowNumber
            ) ->
                toDefinitionView(
                    mapDefinition(
                        resultSet
                    )
                )
        );
    }

    @Transactional(readOnly = true)
    public SettingDefinitionView getDefinition(
        String settingKey
    ) {

        DefinitionRow row =
            requireDefinition(
                normalizeSettingKey(
                    settingKey
                )
            );

        return toDefinitionView(
            row
        );
    }

    @Transactional
    public SettingView upsertSetting(
        UUID organizationId,
        UUID campusId,
        UUID locationId,
        String settingKey,
        UpsertSettingCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        if (command == null) {
            throw new ValidationException(
                "Setting command is required."
            );
        }

        requireId(
            command.updatedBy(),
            "Updated-by user id"
        );

        String normalizedKey =
            normalizeSettingKey(
                settingKey
            );

        lockOrganization(
            organizationId
        );

        requireActorInOrganization(
            organizationId,
            command.updatedBy()
        );

        requireScopeReferences(
            organizationId,
            campusId,
            locationId
        );

        DefinitionRow definition =
            requireDefinition(
                normalizedKey
            );

        if (!definition.runtimeEditable()) {
            throw new ConflictException(
                "Setting is not runtime editable."
            );
        }

        validateOverrideScope(
            definition.scopeType(),
            campusId,
            locationId
        );

        String secretRef =
            normalizeOptionalLimited(
                command.secretRef(),
                SECRET_REF_MAX_LENGTH,
                "Secret reference"
            );

        String valueJson =
            prepareSettingValue(
                definition,
                command.valueJson(),
                secretRef
            );

        SettingRow existing =
            findSetting(
                organizationId,
                campusId,
                locationId,
                normalizedKey
            );

        boolean replay =
            existing != null
                && Objects.equals(
                    existing.valueJson(),
                    valueJson
                )
                && Objects.equals(
                    existing.secretRef(),
                    secretRef
                );

        if (replay) {
            return toSettingView(
                existing
            );
        }

        String beforeJson =
            settingAuditSnapshot(
                existing
            );

        SettingRow stored;

        if (existing == null) {

            UUID id =
                UUID.randomUUID();

            jdbcTemplate.update(
                """
                INSERT INTO system_settings(
                    id,
                    organization_id,
                    campus_id,
                    location_id,
                    setting_key,
                    value,
                    secret_ref,
                    updated_by,
                    updated_at
                )
                VALUES(
                    ?, ?, ?, ?, ?,
                    CAST(? AS JSONB),
                    ?,
                    ?,
                    CURRENT_TIMESTAMP
                )
                """,
                id,
                organizationId,
                campusId,
                locationId,
                normalizedKey,
                valueJson,
                secretRef,
                command.updatedBy()
            );

            stored =
                requireSettingById(
                    organizationId,
                    id
                );

        } else {

            jdbcTemplate.update(
                """
                UPDATE system_settings
                SET
                    value = CAST(? AS JSONB),
                    secret_ref = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND organization_id = ?
                """,
                valueJson,
                secretRef,
                command.updatedBy(),
                existing.id(),
                organizationId
            );

            stored =
                requireSettingById(
                    organizationId,
                    existing.id()
                );
        }

        writeAudit(
            organizationId,
            command.updatedBy(),
            "SYSTEM_SETTING_UPSERT",
            "SYSTEM_SETTING",
            stored.id(),
            beforeJson,
            settingAuditSnapshot(
                stored
            )
        );

        return toSettingView(
            stored
        );
    }

    @Transactional(readOnly = true)
    public ResolvedSettingView resolveSetting(
        UUID organizationId,
        UUID campusId,
        UUID locationId,
        String settingKey
    ) {

        requireOrganization(
            organizationId
        );

        String normalizedKey =
            normalizeSettingKey(
                settingKey
            );

        DefinitionRow definition =
            requireDefinition(
                normalizedKey
            );

        if (
            definition.scopeType()
                == SettingScopeType.GLOBAL
        ) {

            return defaultSetting(
                definition
            );
        }

        requireScopeReferences(
            organizationId,
            campusId,
            locationId
        );

        if (
            definition.scopeType()
                == SettingScopeType.ORGANIZATION
        ) {

            SettingRow row =
                findSetting(
                    organizationId,
                    null,
                    null,
                    normalizedKey
                );

            if (row != null) {
                return resolvedSetting(
                    row,
                    "ORGANIZATION"
                );
            }

            return defaultSetting(
                definition
            );
        }

        if (
            definition.scopeType()
                == SettingScopeType.CAMPUS
        ) {

            if (campusId == null) {
                throw new ValidationException(
                    "Campus-scoped setting requires a campus."
                );
            }

            SettingRow row =
                findSetting(
                    organizationId,
                    campusId,
                    null,
                    normalizedKey
                );

            if (row != null) {
                return resolvedSetting(
                    row,
                    "CAMPUS"
                );
            }

            return defaultSetting(
                definition
            );
        }

        if (
            definition.scopeType()
                == SettingScopeType.LOCATION
        ) {

            if (
                campusId == null
                || locationId == null
            ) {

                throw new ValidationException(
                    "Location-scoped setting requires campus and location."
                );
            }

            SettingRow row =
                findSetting(
                    organizationId,
                    campusId,
                    locationId,
                    normalizedKey
                );

            if (row != null) {
                return resolvedSetting(
                    row,
                    "LOCATION"
                );
            }

            return defaultSetting(
                definition
            );
        }

        if (locationId != null) {

            SettingRow row =
                findSetting(
                    organizationId,
                    campusId,
                    locationId,
                    normalizedKey
                );

            if (row != null) {
                return resolvedSetting(
                    row,
                    "LOCATION"
                );
            }
        }

        if (campusId != null) {

            SettingRow row =
                findSetting(
                    organizationId,
                    campusId,
                    null,
                    normalizedKey
                );

            if (row != null) {
                return resolvedSetting(
                    row,
                    "CAMPUS"
                );
            }
        }

        SettingRow organization =
            findSetting(
                organizationId,
                null,
                null,
                normalizedKey
            );

        if (organization != null) {

            return resolvedSetting(
                organization,
                "ORGANIZATION"
            );
        }

        return defaultSetting(
            definition
        );
    }

    @Transactional
    public void deleteSetting(
        UUID organizationId,
        UUID campusId,
        UUID locationId,
        String settingKey,
        UUID updatedBy
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            updatedBy,
            "Updated-by user id"
        );

        String normalizedKey =
            normalizeSettingKey(
                settingKey
            );

        lockOrganization(
            organizationId
        );

        requireActorInOrganization(
            organizationId,
            updatedBy
        );

        requireScopeReferences(
            organizationId,
            campusId,
            locationId
        );

        DefinitionRow definition =
            requireDefinition(
                normalizedKey
            );

        if (!definition.runtimeEditable()) {
            throw new ConflictException(
                "Setting is not runtime editable."
            );
        }

        validateOverrideScope(
            definition.scopeType(),
            campusId,
            locationId
        );

        SettingRow existing =
            findSetting(
                organizationId,
                campusId,
                locationId,
                normalizedKey
            );

        if (existing == null) {
            throw new NotFoundException(
                "Setting override does not exist."
            );
        }

        String beforeJson =
            settingAuditSnapshot(
                existing
            );

        int deleted =
            jdbcTemplate.update(
                """
                DELETE FROM system_settings
                WHERE id = ?
                  AND organization_id = ?
                """,
                existing.id(),
                organizationId
            );

        if (deleted != 1) {
            throw new ConflictException(
                "Setting changed concurrently."
            );
        }

        writeAudit(
            organizationId,
            updatedBy,
            "SYSTEM_SETTING_DELETE",
            "SYSTEM_SETTING",
            existing.id(),
            beforeJson,
            null
        );
    }

    @Transactional(readOnly = true)
    public PreferenceView getPreference(
        UUID organizationId,
        UUID userId
    ) {

        requireUserInOrganization(
            organizationId,
            userId
        );

        PreferenceRow row =
            findPreference(
                userId
            );

        if (row == null) {

            return new PreferenceView(
                userId,
                "fr",
                null,
                null,
                "{}",
                null
            );
        }

        return toPreferenceView(
            row
        );
    }

    @Transactional
    public PreferenceView upsertPreference(
        UUID organizationId,
        UUID userId,
        UpsertPreferenceCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            userId,
            "User id"
        );

        if (command == null) {
            throw new ValidationException(
                "Preference command is required."
            );
        }

        lockUser(
            organizationId,
            userId
        );

        String locale =
            normalizeLocale(
                command.locale()
            );

        String timezone =
            normalizeTimezone(
                command.timezone()
            );

        requireLocationInOrganization(
            organizationId,
            command.defaultLocationId()
        );

        String accessibility =
            canonicalJson(
                defaultJsonObject(
                    command.accessibilityJson()
                ),
                "Accessibility"
            );

        PreferenceRow existing =
            findPreference(
                userId
            );

        boolean replay =
            existing != null
                && Objects.equals(
                    existing.locale(),
                    locale
                )
                && Objects.equals(
                    existing.timezone(),
                    timezone
                )
                && Objects.equals(
                    existing.defaultLocationId(),
                    command.defaultLocationId()
                )
                && Objects.equals(
                    existing.accessibilityJson(),
                    accessibility
                );

        if (replay) {
            return toPreferenceView(
                existing
            );
        }

        jdbcTemplate.update(
            """
            INSERT INTO user_preferences(
                user_id,
                locale,
                timezone,
                default_location_id,
                accessibility,
                updated_at
            )
            VALUES(
                ?, ?, ?, ?,
                CAST(? AS JSONB),
                CURRENT_TIMESTAMP
            )
            ON CONFLICT(user_id)
            DO UPDATE SET
                locale = EXCLUDED.locale,
                timezone = EXCLUDED.timezone,
                default_location_id =
                    EXCLUDED.default_location_id,
                accessibility =
                    EXCLUDED.accessibility,
                updated_at =
                    CURRENT_TIMESTAMP
            """,
            userId,
            locale,
            timezone,
            command.defaultLocationId(),
            accessibility
        );

        PreferenceRow stored =
            findPreference(
                userId
            );

        if (stored == null) {
            throw new IllegalStateException(
                "Preference missing after mutation."
            );
        }

        return toPreferenceView(
            stored
        );
    }

    @Transactional
    public RetentionView upsertRetention(
        UUID organizationId,
        UpsertRetentionCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        if (command == null) {
            throw new ValidationException(
                "Retention command is required."
            );
        }

        requireId(
            command.updatedBy(),
            "Updated-by user id"
        );

        if (command.action() == null) {
            throw new ValidationException(
                "Retention action is required."
            );
        }

        boolean invalidDays =
            command.retentionDays() != null
                && command.retentionDays() <= 0;

        if (invalidDays) {
            throw new ValidationException(
                "Retention days must be positive."
            );
        }

        String resourceType =
            normalizeRequiredLimited(
                command.resourceType(),
                RESOURCE_TYPE_MAX_LENGTH,
                "Resource type"
            );

        String legalBasis =
            normalizeOptional(
                command.legalBasisNote()
            );

        lockOrganization(
            organizationId
        );

        requireActorInOrganization(
            organizationId,
            command.updatedBy()
        );

        RetentionRow existing =
            findRetention(
                organizationId,
                resourceType
            );

        boolean replay =
            existing != null
                && Objects.equals(
                    existing.retentionDays(),
                    command.retentionDays()
                )
                && existing.action()
                    == command.action()
                && Objects.equals(
                    existing.legalBasisNote(),
                    legalBasis
                )
                && existing.active()
                    == command.active();

        if (replay) {
            return toRetentionView(
                existing
            );
        }

        String beforeJson =
            retentionAuditSnapshot(
                existing
            );

        RetentionRow stored;

        if (existing == null) {

            UUID id =
                UUID.randomUUID();

            jdbcTemplate.update(
                """
                INSERT INTO data_retention_policies(
                    id,
                    organization_id,
                    resource_type,
                    retention_days,
                    action,
                    legal_basis_note,
                    is_active,
                    updated_by,
                    updated_at
                )
                VALUES(
                    ?, ?, ?, ?, ?, ?, ?, ?,
                    CURRENT_TIMESTAMP
                )
                """,
                id,
                organizationId,
                resourceType,
                command.retentionDays(),
                command.action().name(),
                legalBasis,
                command.active(),
                command.updatedBy()
            );

            stored =
                requireRetentionById(
                    organizationId,
                    id
                );

        } else {

            jdbcTemplate.update(
                """
                UPDATE data_retention_policies
                SET
                    retention_days = ?,
                    action = ?,
                    legal_basis_note = ?,
                    is_active = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND organization_id = ?
                """,
                command.retentionDays(),
                command.action().name(),
                legalBasis,
                command.active(),
                command.updatedBy(),
                existing.id(),
                organizationId
            );

            stored =
                requireRetentionById(
                    organizationId,
                    existing.id()
                );
        }

        writeAudit(
            organizationId,
            command.updatedBy(),
            "DATA_RETENTION_POLICY_UPSERT",
            "DATA_RETENTION_POLICY",
            stored.id(),
            beforeJson,
            retentionAuditSnapshot(
                stored
            )
        );

        return toRetentionView(
            stored
        );
    }

    @Transactional(readOnly = true)
    public RetentionView getRetention(
        UUID organizationId,
        String resourceType
    ) {

        requireOrganization(
            organizationId
        );

        String normalized =
            normalizeRequiredLimited(
                resourceType,
                RESOURCE_TYPE_MAX_LENGTH,
                "Resource type"
            );

        RetentionRow row =
            findRetention(
                organizationId,
                normalized
            );

        if (row == null) {
            throw new NotFoundException(
                "Retention policy does not exist."
            );
        }

        return toRetentionView(
            row
        );
    }

    @Transactional(readOnly = true)
    public List<RetentionView> listRetention(
        UUID organizationId
    ) {

        requireOrganization(
            organizationId
        );

        return jdbcTemplate.query(
            """
            SELECT
                id,
                organization_id,
                resource_type,
                retention_days,
                action,
                legal_basis_note,
                is_active,
                updated_by,
                updated_at
            FROM data_retention_policies
            WHERE organization_id = ?
            ORDER BY resource_type
            """,
            (
                resultSet,
                rowNumber
            ) ->
                toRetentionView(
                    mapRetention(
                        resultSet
                    )
                ),
            organizationId
        );
    }

    private DefinitionRow requireDefinition(
        String settingKey
    ) {

        List<DefinitionRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    setting_key,
                    value_type,
                    scope_type,
                    default_value::text AS default_value_json,
                    description,
                    is_secret,
                    is_runtime_editable,
                    validation_rules::text AS validation_rules_json,
                    created_at,
                    updated_at
                FROM setting_definitions
                WHERE setting_key = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    mapDefinition(
                        resultSet
                    ),
                settingKey
            );

        if (rows.isEmpty()) {
            throw new NotFoundException(
                "Setting definition does not exist."
            );
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Multiple setting definitions found."
            );
        }

        return rows.get(0);
    }

    private SettingRow findSetting(
        UUID organizationId,
        UUID campusId,
        UUID locationId,
        String settingKey
    ) {

        List<SettingRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    organization_id,
                    campus_id,
                    location_id,
                    setting_key,
                    value::text AS value_json,
                    secret_ref,
                    updated_by,
                    updated_at
                FROM system_settings
                WHERE organization_id = ?
                  AND campus_id IS NOT DISTINCT FROM ?
                  AND location_id IS NOT DISTINCT FROM ?
                  AND setting_key = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    mapSetting(
                        resultSet
                    ),
                organizationId,
                campusId,
                locationId,
                settingKey
            );

        if (rows.isEmpty()) {
            return null;
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Multiple setting overrides found."
            );
        }

        return rows.get(0);
    }

    private SettingRow requireSettingById(
        UUID organizationId,
        UUID id
    ) {

        List<SettingRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    organization_id,
                    campus_id,
                    location_id,
                    setting_key,
                    value::text AS value_json,
                    secret_ref,
                    updated_by,
                    updated_at
                FROM system_settings
                WHERE id = ?
                  AND organization_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    mapSetting(
                        resultSet
                    ),
                id,
                organizationId
            );

        if (rows.isEmpty()) {
            throw new ConflictException(
                "Setting missing after mutation."
            );
        }

        return rows.get(0);
    }

    private DefinitionRow mapDefinition(
        ResultSet resultSet
    ) throws SQLException {

        return new DefinitionRow(
            resultSet.getString("setting_key"),
            SettingValueType.valueOf(
                resultSet.getString("value_type")
            ),
            SettingScopeType.valueOf(
                resultSet.getString("scope_type")
            ),
            resultSet.getString("default_value_json"),
            resultSet.getString("description"),
            resultSet.getBoolean("is_secret"),
            resultSet.getBoolean("is_runtime_editable"),
            resultSet.getString("validation_rules_json"),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "updated_at",
                OffsetDateTime.class
            )
        );
    }

    private SettingRow mapSetting(
        ResultSet resultSet
    ) throws SQLException {

        return new SettingRow(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "organization_id",
                UUID.class
            ),
            resultSet.getObject(
                "campus_id",
                UUID.class
            ),
            resultSet.getObject(
                "location_id",
                UUID.class
            ),
            resultSet.getString("setting_key"),
            resultSet.getString("value_json"),
            resultSet.getString("secret_ref"),
            resultSet.getObject(
                "updated_by",
                UUID.class
            ),
            resultSet.getObject(
                "updated_at",
                OffsetDateTime.class
            )
        );
    }

    private SettingDefinitionView toDefinitionView(
        DefinitionRow row
    ) {

        return new SettingDefinitionView(
            row.settingKey(),
            row.valueType(),
            row.scopeType(),
            row.secret()
                ? null
                : row.defaultValueJson(),
            row.description(),
            row.secret(),
            row.runtimeEditable(),
            row.validationRulesJson(),
            row.createdAt(),
            row.updatedAt()
        );
    }

    private SettingView toSettingView(
        SettingRow row
    ) {

        return new SettingView(
            row.id(),
            row.organizationId(),
            row.campusId(),
            row.locationId(),
            row.settingKey(),
            row.secretRef() == null
                ? row.valueJson()
                : null,
            row.secretRef() != null,
            row.updatedBy(),
            row.updatedAt()
        );
    }

    private ResolvedSettingView resolvedSetting(
        SettingRow row,
        String source
    ) {

        return new ResolvedSettingView(
            row.settingKey(),
            row.secretRef() == null
                ? row.valueJson()
                : null,
            row.secretRef() != null,
            source,
            row.campusId(),
            row.locationId()
        );
    }

    private ResolvedSettingView defaultSetting(
        DefinitionRow row
    ) {

        return new ResolvedSettingView(
            row.settingKey(),
            row.secret()
                ? null
                : row.defaultValueJson(),
            false,
            "DEFAULT",
            null,
            null
        );
    }

    private String prepareSettingValue(
        DefinitionRow definition,
        String rawValueJson,
        String secretRef
    ) {

        if (definition.secret()) {

            boolean rawPresent =
                rawValueJson != null
                    && !rawValueJson.trim().isEmpty();

            if (rawPresent) {
                throw new ValidationException(
                    "Secret setting must not contain a raw value."
                );
            }

            if (secretRef == null) {
                throw new ValidationException(
                    "Secret setting requires a secret reference."
                );
            }

            return null;
        }

        if (secretRef != null) {
            throw new ValidationException(
                "Non-secret setting must not contain a secret reference."
            );
        }

        boolean missing =
            rawValueJson == null
                || rawValueJson.trim().isEmpty();

        if (missing) {
            throw new ValidationException(
                "Setting value is required."
            );
        }

        String canonical =
            canonicalJson(
                rawValueJson,
                "Setting value"
            );

        boolean correctType =
            valueMatchesType(
                definition.valueType(),
                canonical
            );

        if (!correctType) {
            throw new ValidationException(
                "Setting value does not match definition value type."
            );
        }

        return canonical;
    }

    private boolean valueMatchesType(
        SettingValueType type,
        String canonical
    ) {

        if (type == SettingValueType.JSON) {
            return true;
        }

        if (
            type == SettingValueType.STRING
                || type == SettingValueType.DURATION
        ) {

            return hasJsonType(
                canonical,
                "string"
            );
        }

        if (type == SettingValueType.BOOLEAN) {
            return hasJsonType(
                canonical,
                "boolean"
            );
        }

        if (type == SettingValueType.DECIMAL) {
            return hasJsonType(
                canonical,
                "number"
            );
        }

        Boolean integer =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    jsonb_typeof(
                        CAST(? AS JSONB)
                    ) = 'number'
                    AND (
                        CAST(? AS JSONB)
                        #>> '{}'
                    ) ~ '^-?[0-9]+$'
                """,
                Boolean.class,
                canonical,
                canonical
            );

        return Boolean.TRUE.equals(integer);
    }

    private boolean hasJsonType(
        String canonical,
        String type
    ) {

        Boolean result =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    jsonb_typeof(
                        CAST(? AS JSONB)
                    ) = ?
                """,
                Boolean.class,
                canonical,
                type
            );

        return Boolean.TRUE.equals(result);
    }

    private String settingAuditSnapshot(
        SettingRow row
    ) {

        if (row == null) {
            return null;
        }

        return jdbcTemplate.queryForObject(
            """
            SELECT jsonb_build_object(
                'settingKey', ?,
                'campusId', CAST(? AS TEXT),
                'locationId', CAST(? AS TEXT),
                'secretConfigured', ?,
                'value',
                    CASE
                        WHEN ?
                            THEN NULL
                        ELSE CAST(? AS JSONB)
                    END
            )::text
            """,
            String.class,
            row.settingKey(),
            row.campusId(),
            row.locationId(),
            row.secretRef() != null,
            row.secretRef() != null,
            row.valueJson()
        );
    }

    private PreferenceRow findPreference(
        UUID userId
    ) {

        List<PreferenceRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    user_id,
                    locale,
                    timezone,
                    default_location_id,
                    accessibility::text AS accessibility_json,
                    updated_at
                FROM user_preferences
                WHERE user_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    mapPreference(
                        resultSet
                    ),
                userId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(0);
    }

    private PreferenceRow mapPreference(
        ResultSet resultSet
    ) throws SQLException {

        return new PreferenceRow(
            resultSet.getObject(
                "user_id",
                UUID.class
            ),
            resultSet.getString("locale"),
            resultSet.getString("timezone"),
            resultSet.getObject(
                "default_location_id",
                UUID.class
            ),
            resultSet.getString("accessibility_json"),
            resultSet.getObject(
                "updated_at",
                OffsetDateTime.class
            )
        );
    }

    private PreferenceView toPreferenceView(
        PreferenceRow row
    ) {

        return new PreferenceView(
            row.userId(),
            row.locale(),
            row.timezone(),
            row.defaultLocationId(),
            row.accessibilityJson(),
            row.updatedAt()
        );
    }

    private String normalizeLocale(
        String value
    ) {

        if (value == null) {
            throw new ValidationException(
                "Locale is required."
            );
        }

        String normalized =
            value.trim();

        boolean empty =
            normalized.isEmpty();

        boolean tooLong =
            normalized.length() > LOCALE_MAX_LENGTH;

        boolean badPattern =
            !LOCALE_PATTERN
                .matcher(normalized)
                .matches();

        if (
            empty
                || tooLong
                || badPattern
        ) {

            throw new ValidationException(
                "Locale is invalid."
            );
        }

        return normalized;
    }

    private String normalizeTimezone(
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

        if (normalized.length() > TIMEZONE_MAX_LENGTH) {
            throw new ValidationException(
                "Timezone exceeds maximum length."
            );
        }

        try {

            ZoneId.of(
                normalized
            );

        } catch (RuntimeException exception) {

            throw new ValidationException(
                "Timezone is invalid."
            );
        }

        return normalized;
    }

    private RetentionRow findRetention(
        UUID organizationId,
        String resourceType
    ) {

        List<RetentionRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    organization_id,
                    resource_type,
                    retention_days,
                    action,
                    legal_basis_note,
                    is_active,
                    updated_by,
                    updated_at
                FROM data_retention_policies
                WHERE organization_id = ?
                  AND resource_type = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    mapRetention(
                        resultSet
                    ),
                organizationId,
                resourceType
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(0);
    }

    private RetentionRow requireRetentionById(
        UUID organizationId,
        UUID id
    ) {

        List<RetentionRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    organization_id,
                    resource_type,
                    retention_days,
                    action,
                    legal_basis_note,
                    is_active,
                    updated_by,
                    updated_at
                FROM data_retention_policies
                WHERE id = ?
                  AND organization_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    mapRetention(
                        resultSet
                    ),
                id,
                organizationId
            );

        if (rows.isEmpty()) {
            throw new ConflictException(
                "Retention policy missing after mutation."
            );
        }

        return rows.get(0);
    }

    private RetentionRow mapRetention(
        ResultSet resultSet
    ) throws SQLException {

        return new RetentionRow(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "organization_id",
                UUID.class
            ),
            resultSet.getString("resource_type"),
            resultSet.getObject(
                "retention_days",
                Integer.class
            ),
            DataRetentionAction.valueOf(
                resultSet.getString("action")
            ),
            resultSet.getString("legal_basis_note"),
            resultSet.getBoolean("is_active"),
            resultSet.getObject(
                "updated_by",
                UUID.class
            ),
            resultSet.getObject(
                "updated_at",
                OffsetDateTime.class
            )
        );
    }

    private RetentionView toRetentionView(
        RetentionRow row
    ) {

        return new RetentionView(
            row.id(),
            row.organizationId(),
            row.resourceType(),
            row.retentionDays(),
            row.action(),
            row.legalBasisNote(),
            row.active(),
            row.updatedBy(),
            row.updatedAt()
        );
    }

    private String retentionAuditSnapshot(
        RetentionRow row
    ) {

        if (row == null) {
            return null;
        }

        return jdbcTemplate.queryForObject(
            """
            SELECT jsonb_build_object(
                'resourceType', ?,
                'retentionDays', ?,
                'action', ?,
                'legalBasisNote', ?,
                'active', ?
            )::text
            """,
            String.class,
            row.resourceType(),
            row.retentionDays(),
            row.action().name(),
            row.legalBasisNote(),
            row.active()
        );
    }

    private String canonicalJson(
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
                throw new ValidationException(
                    label + " must contain valid JSON."
                );
            }

            return canonical;

        } catch (DataAccessException exception) {

            throw new ValidationException(
                label + " must contain valid JSON."
            );
        }
    }

    private void validateOverrideScope(
        SettingScopeType scope,
        UUID campusId,
        UUID locationId
    ) {

        if (scope == SettingScopeType.GLOBAL) {
            throw new ValidationException(
                "Global definition cannot be overridden."
            );
        }

        if (scope == SettingScopeType.ORGANIZATION) {

            boolean invalid =
                campusId != null
                    || locationId != null;

            if (invalid) {
                throw new ValidationException(
                    "Organization setting cannot define campus or location."
                );
            }
        }

        if (scope == SettingScopeType.CAMPUS) {

            boolean invalid =
                campusId == null
                    || locationId != null;

            if (invalid) {
                throw new ValidationException(
                    "Campus setting requires campus only."
                );
            }
        }

        if (scope == SettingScopeType.LOCATION) {

            boolean invalid =
                campusId == null
                    || locationId == null;

            if (invalid) {
                throw new ValidationException(
                    "Location setting requires campus and location."
                );
            }
        }

        if (scope == SettingScopeType.ANY) {

            boolean invalid =
                locationId != null
                    && campusId == null;

            if (invalid) {
                throw new ValidationException(
                    "Location override requires campus."
                );
            }
        }
    }

    private void requireScopeReferences(
        UUID organizationId,
        UUID campusId,
        UUID locationId
    ) {

        boolean locationWithoutCampus =
            locationId != null
                && campusId == null;

        if (locationWithoutCampus) {
            throw new ValidationException(
                "Location scope requires a campus."
            );
        }

        if (campusId != null) {

            Long count =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM campuses
                    WHERE id = ?
                      AND organization_id = ?
                    """,
                    Long.class,
                    campusId,
                    organizationId
                );

            if (
                count == null
                    || count != 1L
            ) {

                throw new NotFoundException(
                    "Campus does not exist in organization."
                );
            }
        }

        if (locationId != null) {

            Long count =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM locations l
                    JOIN campuses c
                      ON c.id = l.campus_id
                    WHERE l.id = ?
                      AND c.id = ?
                      AND c.organization_id = ?
                    """,
                    Long.class,
                    locationId,
                    campusId,
                    organizationId
                );

            if (
                count == null
                    || count != 1L
            ) {

                throw new NotFoundException(
                    "Location does not exist in campus and organization."
                );
            }
        }
    }

    private void requireLocationInOrganization(
        UUID organizationId,
        UUID locationId
    ) {

        if (locationId == null) {
            return;
        }

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM locations l
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE l.id = ?
                  AND c.organization_id = ?
                """,
                Long.class,
                locationId,
                organizationId
            );

        if (
            count == null
                || count != 1L
        ) {

            throw new NotFoundException(
                "Location does not exist in organization."
            );
        }
    }

    private void requireOrganization(
        UUID organizationId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM organizations
                WHERE id = ?
                """,
                Long.class,
                organizationId
            );

        if (
            count == null
                || count != 1L
        ) {

            throw new NotFoundException(
                "Organization does not exist."
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
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                organizationId
            );

        if (rows.isEmpty()) {
            throw new NotFoundException(
                "Organization does not exist."
            );
        }
    }

    private void requireActorInOrganization(
        UUID organizationId,
        UUID userId
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM users
                WHERE id = ?
                  AND organization_id = ?
                """,
                Long.class,
                userId,
                organizationId
            );

        if (
            count == null
                || count != 1L
        ) {

            throw new NotFoundException(
                "User does not exist in organization."
            );
        }
    }

    private void requireUserInOrganization(
        UUID organizationId,
        UUID userId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            userId,
            "User id"
        );

        requireActorInOrganization(
            organizationId,
            userId
        );
    }

    private void lockUser(
        UUID organizationId,
        UUID userId
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
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                userId,
                organizationId
            );

        if (rows.isEmpty()) {
            throw new NotFoundException(
                "User does not exist in organization."
            );
        }
    }

    private void writeAudit(
        UUID organizationId,
        UUID userId,
        String action,
        String resourceType,
        UUID resourceId,
        String beforeJson,
        String afterJson
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO audit_logs(
                organization_id,
                user_id,
                action,
                resource_type,
                resource_id,
                before_data,
                after_data,
                source,
                result
            )
            VALUES(
                ?, ?, ?, ?, ?,
                CAST(? AS JSONB),
                CAST(? AS JSONB),
                'BACKEND',
                'SUCCESS'
            )
            """,
            organizationId,
            userId,
            action,
            resourceType,
            resourceId,
            beforeJson,
            afterJson
        );
    }

    private String normalizeSettingKey(
        String value
    ) {

        return normalizeRequiredLimited(
            value,
            SETTING_KEY_MAX_LENGTH,
            "Setting key"
        );
    }

    private String normalizeRequiredLimited(
        String value,
        int maxLength,
        String label
    ) {

        if (value == null) {
            throw new ValidationException(
                label + " is required."
            );
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {
            throw new ValidationException(
                label + " is required."
            );
        }

        if (normalized.length() > maxLength) {
            throw new ValidationException(
                label + " exceeds maximum length."
            );
        }

        return normalized;
    }

    private String normalizeOptionalLimited(
        String value,
        int maxLength,
        String label
    ) {

        String normalized =
            normalizeOptional(
                value
            );

        if (normalized == null) {
            return null;
        }

        if (normalized.length() > maxLength) {
            throw new ValidationException(
                label + " exceeds maximum length."
            );
        }

        return normalized;
    }

    private String normalizeOptional(
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

    private String defaultJsonObject(
        String value
    ) {

        if (value == null) {
            return "{}";
        }

        if (value.trim().isEmpty()) {
            return "{}";
        }

        return value;
    }

    private void requireId(
        UUID value,
        String label
    ) {

        if (value == null) {
            throw new ValidationException(
                label + " is required."
            );
        }
    }
}