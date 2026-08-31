package com.sup2i.food.notification.service;

import com.sup2i.food.notification.api.dto.NotificationPreferenceResponse;
import com.sup2i.food.notification.api.dto.UpsertNotificationPreferenceCommand;
import com.sup2i.food.notification.domain.NotificationCategory;
import com.sup2i.food.notification.domain.NotificationChannel;
import com.sup2i.food.notification.exception.NotificationConflictException;
import com.sup2i.food.notification.exception.NotificationNotFoundException;
import com.sup2i.food.notification.exception.NotificationValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationPreferenceService {

    private final JdbcTemplate jdbcTemplate;

    public NotificationPreferenceService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getOrDefault(
        UUID organizationId,
        UUID userId,
        NotificationCategory category
    ) {

        requireContext(
            organizationId,
            userId,
            category
        );

        requireTargetUser(
            organizationId,
            userId
        );

        NotificationPreferenceResponse stored =
            find(
                organizationId,
                userId,
                category,
                false
            );

        if (stored != null) {
            return stored;
        }

        return new NotificationPreferenceResponse(
            null,
            userId,
            category,
            true,
            true,
            true,
            null,
            null,
            null,
            false
        );
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> list(
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

        requireTargetUser(
            organizationId,
            userId
        );

        return jdbcTemplate.query(
            """
            SELECT
                p.id,
                p.user_id,
                p.category,
                p.push_enabled,
                p.email_enabled,
                p.in_app_enabled,
                p.quiet_hours_start,
                p.quiet_hours_end,
                p.updated_at
            FROM notification_preferences p
            JOIN users u
              ON u.id = p.user_id
            WHERE p.user_id = ?
              AND u.organization_id = ?
            ORDER BY
                p.category ASC,
                p.id ASC
            """,
            (resultSet, rowNumber) ->
                map(resultSet),
            userId,
            organizationId
        );
    }

    @Transactional
    public NotificationPreferenceResponse upsert(
        UUID organizationId,
        UUID userId,
        NotificationCategory category,
        UpsertNotificationPreferenceCommand command
    ) {

        requireContext(
            organizationId,
            userId,
            category
        );

        if (command == null) {
            throw new NotificationValidationException(
                "Notification preference payload is required."
            );
        }

        lockTargetUser(
            organizationId,
            userId
        );

        NotificationPreferenceResponse stored =
            find(
                organizationId,
                userId,
                category,
                true
            );

        if (stored == null) {

            try {

                jdbcTemplate.update(
                    """
                    INSERT INTO notification_preferences(
                        id,
                        user_id,
                        category,
                        push_enabled,
                        email_enabled,
                        in_app_enabled,
                        quiet_hours_start,
                        quiet_hours_end,
                        updated_at
                    )
                    VALUES(
                        ?, ?, ?, ?, ?, ?,
                        ?, ?,
                        CURRENT_TIMESTAMP
                    )
                    """,
                    UUID.randomUUID(),
                    userId,
                    category.name(),
                    command.pushEnabled(),
                    command.emailEnabled(),
                    command.inAppEnabled(),
                    command.quietHoursStart(),
                    command.quietHoursEnd()
                );

            } catch (DataIntegrityViolationException exception) {

                throw new NotificationConflictException(
                    "Notification preference conflicts with an existing resource."
                );
            }
        }
        else {

            int updated =
                jdbcTemplate.update(
                    """
                    UPDATE notification_preferences
                    SET
                        push_enabled = ?,
                        email_enabled = ?,
                        in_app_enabled = ?,
                        quiet_hours_start = ?,
                        quiet_hours_end = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                      AND user_id = ?
                      AND category = ?
                    """,
                    command.pushEnabled(),
                    command.emailEnabled(),
                    command.inAppEnabled(),
                    command.quietHoursStart(),
                    command.quietHoursEnd(),
                    stored.id(),
                    userId,
                    category.name()
                );

            if (updated != 1) {
                throw new NotificationConflictException(
                    "Notification preference changed concurrently."
                );
            }
        }

        NotificationPreferenceResponse result =
            find(
                organizationId,
                userId,
                category,
                false
            );

        if (result == null) {
            throw new NotificationNotFoundException(
                "Notification preference was not persisted."
            );
        }

        return result;
    }

    @Transactional(readOnly = true)
    public boolean isChannelEnabled(
        UUID organizationId,
        UUID userId,
        NotificationCategory category,
        NotificationChannel channel
    ) {

        requireContext(
            organizationId,
            userId,
            category
        );

        if (channel == null) {
            throw new NotificationValidationException(
                "Notification channel is required."
            );
        }

        NotificationPreferenceResponse preference =
            getOrDefault(
                organizationId,
                userId,
                category
            );

        return switch (channel) {
            case PUSH -> preference.pushEnabled();
            case EMAIL -> preference.emailEnabled();
            case IN_APP -> preference.inAppEnabled();
        };
    }

    private NotificationPreferenceResponse find(
        UUID organizationId,
        UUID userId,
        NotificationCategory category,
        boolean lock
    ) {

        String sql =
            """
            SELECT
                p.id,
                p.user_id,
                p.category,
                p.push_enabled,
                p.email_enabled,
                p.in_app_enabled,
                p.quiet_hours_start,
                p.quiet_hours_end,
                p.updated_at
            FROM notification_preferences p
            JOIN users u
              ON u.id = p.user_id
            WHERE p.user_id = ?
              AND p.category = ?
              AND u.organization_id = ?
            """;

        if (lock) {
            sql = sql + " FOR UPDATE OF p";
        }

        List<NotificationPreferenceResponse> rows =
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                    map(resultSet),
                userId,
                category.name(),
                organizationId
            );

        if (rows.size() > 1) {
            throw new NotificationConflictException(
                "Multiple notification preferences matched one category."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private void requireTargetUser(
        UUID organizationId,
        UUID userId
    ) {

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM users
                WHERE id = ?
                  AND organization_id = ?
                """,
                Integer.class,
                userId,
                organizationId
            );

        if (
            count == null
            || count != 1
        ) {
            throw new NotificationNotFoundException(
                "Target user does not exist in organization."
            );
        }
    }

    private void lockTargetUser(
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
                (resultSet, rowNumber) ->
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                userId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new NotificationNotFoundException(
                "Target user does not exist in organization."
            );
        }
    }

    private void requireContext(
        UUID organizationId,
        UUID userId,
        NotificationCategory category
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            userId,
            "User id"
        );

        if (category == null) {
            throw new NotificationValidationException(
                "Notification category is required."
            );
        }
    }

    private void requireId(
        UUID value,
        String label
    ) {

        if (value == null) {
            throw new NotificationValidationException(
                label + " is required."
            );
        }
    }

    private NotificationPreferenceResponse map(
        java.sql.ResultSet resultSet
    ) throws java.sql.SQLException {

        return new NotificationPreferenceResponse(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "user_id",
                UUID.class
            ),
            NotificationCategory.valueOf(
                resultSet.getString(
                    "category"
                )
            ),
            resultSet.getBoolean(
                "push_enabled"
            ),
            resultSet.getBoolean(
                "email_enabled"
            ),
            resultSet.getBoolean(
                "in_app_enabled"
            ),
            resultSet.getObject(
                "quiet_hours_start",
                LocalTime.class
            ),
            resultSet.getObject(
                "quiet_hours_end",
                LocalTime.class
            ),
            resultSet.getObject(
                "updated_at",
                OffsetDateTime.class
            ),
            true
        );
    }
}