package com.sup2i.food.notification.service;

import com.sup2i.food.notification.api.dto.CreateNotificationCommand;
import com.sup2i.food.notification.api.dto.NotificationResponse;
import com.sup2i.food.notification.domain.NotificationChannel;
import com.sup2i.food.notification.domain.NotificationPriority;
import com.sup2i.food.notification.domain.NotificationStatus;
import com.sup2i.food.notification.exception.NotificationConflictException;
import com.sup2i.food.notification.exception.NotificationNotFoundException;
import com.sup2i.food.notification.exception.NotificationValidationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class NotificationService {

    private static final int TYPE_MAX_LENGTH = 50;
    private static final int TITLE_MAX_LENGTH = 180;
    private static final int DEDUPLICATION_KEY_MAX_LENGTH = 180;
    private static final int MAX_LIST_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public NotificationService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public NotificationResponse enqueue(
        UUID organizationId,
        UUID notificationId,
        UUID userId,
        CreateNotificationCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            notificationId,
            "Notification id"
        );

        requireId(
            userId,
            "User id"
        );

        validateCommand(command);

        String type =
            requiredText(
                command.type(),
                "Notification type",
                TYPE_MAX_LENGTH
            );

        String title =
            requiredText(
                command.title(),
                "Notification title",
                TITLE_MAX_LENGTH
            );

        String body =
            requiredBody(
                command.body()
            );

        String deduplicationKey =
            nullableText(
                command.deduplicationKey(),
                DEDUPLICATION_KEY_MAX_LENGTH,
                "Deduplication key"
            );

        NotificationPriority priority =
            command.priority() == null
                ? NotificationPriority.NORMAL
                : command.priority();

        String payloadJson =
            canonicalPayload(
                command.payloadJson()
            );

        lockTargetUser(
            organizationId,
            userId
        );

        NotificationResponse existing =
            findById(
                organizationId,
                notificationId,
                false
            );

        if (existing != null) {

            boolean same =
                existing.userId().equals(userId)
                    && existing.type().equals(type)
                    && existing.channel() == command.channel()
                    && existing.title().equals(title)
                    && existing.body().equals(body)
                    && Objects.equals(
                        existing.payloadJson(),
                        payloadJson
                    )
                    && existing.priority() == priority
                    && Objects.equals(
                        existing.deduplicationKey(),
                        deduplicationKey
                    )
                    && samePostgresTimestamp(
                        existing.scheduledAt(),
                        command.scheduledAt()
                    );

            if (same) {
                return replay(existing);
            }

            throw new NotificationConflictException(
                "Notification identifier is already used by another payload."
            );
        }

        if (deduplicationKey != null) {

            NotificationResponse deduplicated =
                findActiveDeduplication(
                    organizationId,
                    userId,
                    command.channel(),
                    deduplicationKey,
                    true
                );

            if (deduplicated != null) {
                return replay(deduplicated);
            }
        }

        try {

            jdbcTemplate.update(
                """
                INSERT INTO notifications(
                    id,
                    user_id,
                    type,
                    channel,
                    title,
                    body,
                    payload,
                    status,
                    priority,
                    deduplication_key,
                    scheduled_at,
                    retry_count,
                    last_error
                )
                VALUES(
                    ?, ?, ?, ?, ?, ?,
                    CAST(? AS JSONB),
                    'PENDING',
                    ?, ?, ?,
                    0,
                    NULL
                )
                """,
                notificationId,
                userId,
                type,
                command.channel().name(),
                title,
                body,
                payloadJson,
                priority.name(),
                deduplicationKey,
                command.scheduledAt()
            );

        } catch (DataIntegrityViolationException exception) {

            if (deduplicationKey != null) {

                NotificationResponse deduplicated =
                    findActiveDeduplication(
                        organizationId,
                        userId,
                        command.channel(),
                        deduplicationKey,
                        false
                    );

                if (deduplicated != null) {
                    return replay(deduplicated);
                }
            }

            throw new NotificationConflictException(
                "Notification conflicts with an existing resource."
            );
        }

        return get(
            organizationId,
            notificationId
        );
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(
        UUID organizationId,
        UUID notificationId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            notificationId,
            "Notification id"
        );

        NotificationResponse result =
            findById(
                organizationId,
                notificationId,
                false
            );

        if (result == null) {
            throw new NotificationNotFoundException(
                "Notification does not exist."
            );
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(
        UUID organizationId,
        UUID userId,
        NotificationStatus status,
        int limit
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

        int normalizedLimit =
            normalizeLimit(
                limit
            );

        if (status == null) {

            return jdbcTemplate.query(
                """
                SELECT
                    n.id,
                    n.user_id,
                    n.type,
                    n.channel,
                    n.title,
                    n.body,
                    n.payload::text AS payload_json,
                    n.status,
                    n.priority,
                    n.deduplication_key,
                    n.scheduled_at,
                    n.retry_count,
                    n.last_error,
                    n.sent_at,
                    n.read_at,
                    n.created_at
                FROM notifications n
                JOIN users u
                  ON u.id = n.user_id
                WHERE n.user_id = ?
                  AND u.organization_id = ?
                ORDER BY
                    n.created_at DESC,
                    n.id DESC
                LIMIT ?
                """,
                (resultSet, rowNumber) ->
                    map(
                        resultSet,
                        false
                    ),
                userId,
                organizationId,
                normalizedLimit
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                n.id,
                n.user_id,
                n.type,
                n.channel,
                n.title,
                n.body,
                n.payload::text AS payload_json,
                n.status,
                n.priority,
                n.deduplication_key,
                n.scheduled_at,
                n.retry_count,
                n.last_error,
                n.sent_at,
                n.read_at,
                n.created_at
            FROM notifications n
            JOIN users u
              ON u.id = n.user_id
            WHERE n.user_id = ?
              AND u.organization_id = ?
              AND n.status = ?
            ORDER BY
                n.created_at DESC,
                n.id DESC
            LIMIT ?
            """,
            (resultSet, rowNumber) ->
                map(
                    resultSet,
                    false
                ),
            userId,
            organizationId,
            status.name(),
            normalizedLimit
        );
    }

    @Transactional
    public NotificationResponse markRead(
        UUID organizationId,
        UUID userId,
        UUID notificationId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            userId,
            "User id"
        );

        requireId(
            notificationId,
            "Notification id"
        );

        NotificationResponse stored =
            findForUserById(
                organizationId,
                userId,
                notificationId,
                true
            );

        if (stored == null) {
            throw new NotificationNotFoundException(
                "Notification does not exist for user."
            );
        }

        if (stored.status() == NotificationStatus.READ) {
            return replay(stored);
        }

        if (stored.status() != NotificationStatus.SENT) {
            throw new NotificationConflictException(
                "Only a SENT notification can be marked READ."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE notifications
                SET
                    status = 'READ',
                    read_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND user_id = ?
                  AND status = 'SENT'
                """,
                notificationId,
                userId
            );

        if (updated != 1) {
            throw new NotificationConflictException(
                "Notification changed concurrently."
            );
        }

        NotificationResponse result =
            findForUserById(
                organizationId,
                userId,
                notificationId,
                false
            );

        if (result == null) {
            throw new NotificationNotFoundException(
                "Notification does not exist for user."
            );
        }

        return result;
    }

    private NotificationResponse findById(
        UUID organizationId,
        UUID notificationId,
        boolean lock
    ) {

        String sql =
            """
            SELECT
                n.id,
                n.user_id,
                n.type,
                n.channel,
                n.title,
                n.body,
                n.payload::text AS payload_json,
                n.status,
                n.priority,
                n.deduplication_key,
                n.scheduled_at,
                n.retry_count,
                n.last_error,
                n.sent_at,
                n.read_at,
                n.created_at
            FROM notifications n
            JOIN users u
              ON u.id = n.user_id
            WHERE n.id = ?
              AND u.organization_id = ?
            """;

        if (lock) {
            sql = sql + " FOR UPDATE OF n";
        }

        List<NotificationResponse> rows =
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                    map(
                        resultSet,
                        false
                    ),
                notificationId,
                organizationId
            );

        if (rows.size() > 1) {
            throw new NotificationConflictException(
                "Multiple notifications matched one identifier."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private NotificationResponse findForUserById(
        UUID organizationId,
        UUID userId,
        UUID notificationId,
        boolean lock
    ) {

        String sql =
            """
            SELECT
                n.id,
                n.user_id,
                n.type,
                n.channel,
                n.title,
                n.body,
                n.payload::text AS payload_json,
                n.status,
                n.priority,
                n.deduplication_key,
                n.scheduled_at,
                n.retry_count,
                n.last_error,
                n.sent_at,
                n.read_at,
                n.created_at
            FROM notifications n
            JOIN users u
              ON u.id = n.user_id
            WHERE n.id = ?
              AND n.user_id = ?
              AND u.organization_id = ?
            """;

        if (lock) {
            sql = sql + " FOR UPDATE OF n";
        }

        List<NotificationResponse> rows =
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                    map(
                        resultSet,
                        false
                    ),
                notificationId,
                userId,
                organizationId
            );

        if (rows.size() > 1) {
            throw new NotificationConflictException(
                "Multiple notifications matched one user resource."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private NotificationResponse findActiveDeduplication(
        UUID organizationId,
        UUID userId,
        NotificationChannel channel,
        String deduplicationKey,
        boolean lock
    ) {

        String sql =
            """
            SELECT
                n.id,
                n.user_id,
                n.type,
                n.channel,
                n.title,
                n.body,
                n.payload::text AS payload_json,
                n.status,
                n.priority,
                n.deduplication_key,
                n.scheduled_at,
                n.retry_count,
                n.last_error,
                n.sent_at,
                n.read_at,
                n.created_at
            FROM notifications n
            JOIN users u
              ON u.id = n.user_id
            WHERE n.user_id = ?
              AND u.organization_id = ?
              AND n.channel = ?
              AND n.deduplication_key = ?
              AND n.status IN ('PENDING','SENT')
            ORDER BY
                n.created_at ASC,
                n.id ASC
            LIMIT 1
            """;

        if (lock) {
            sql = sql + " FOR UPDATE OF n";
        }

        List<NotificationResponse> rows =
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                    map(
                        resultSet,
                        false
                    ),
                userId,
                organizationId,
                channel.name(),
                deduplicationKey
            );

        return rows.isEmpty()
            ? null
            : rows.get(0);
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

    private String canonicalPayload(
        String rawPayload
    ) {

        if (rawPayload == null) {
            return null;
        }

        String normalized =
            rawPayload.trim();

        if (normalized.isEmpty()) {
            throw new NotificationValidationException(
                "Notification payload must be valid JSON or null."
            );
        }

        try {

            return jdbcTemplate.queryForObject(
                """
                SELECT CAST(
                    CAST(? AS JSONB)
                    AS TEXT
                )
                """,
                String.class,
                normalized
            );

        } catch (DataAccessException exception) {

            throw new NotificationValidationException(
                "Notification payload must be valid JSON."
            );
        }
    }

    private void validateCommand(
        CreateNotificationCommand command
    ) {

        if (command == null) {
            throw new NotificationValidationException(
                "Notification payload is required."
            );
        }

        if (command.channel() == null) {
            throw new NotificationValidationException(
                "Notification channel is required."
            );
        }
    }

    private String requiredBody(
        String value
    ) {

        if (value == null) {
            throw new NotificationValidationException(
                "Notification body is required."
            );
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {
            throw new NotificationValidationException(
                "Notification body is required."
            );
        }

        return normalized;
    }

    private String requiredText(
        String value,
        String label,
        int maxLength
    ) {

        String normalized =
            value == null
                ? null
                : value.trim();

        if (
            normalized == null
            || normalized.isEmpty()
        ) {
            throw new NotificationValidationException(
                label + " is required."
            );
        }

        if (normalized.length() > maxLength) {
            throw new NotificationValidationException(
                label + " is too long."
            );
        }

        return normalized;
    }

    private String nullableText(
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
            throw new NotificationValidationException(
                label + " is too long."
            );
        }

        return normalized;
    }

    private int normalizeLimit(
        int limit
    ) {

        if (limit <= 0) {
            throw new NotificationValidationException(
                "Notification list limit must be positive."
            );
        }

        return Math.min(
            limit,
            MAX_LIST_LIMIT
        );
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

    private NotificationResponse map(
        java.sql.ResultSet resultSet,
        boolean replayed
    ) throws java.sql.SQLException {

        return new NotificationResponse(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("user_id", UUID.class),
            resultSet.getString("type"),
            NotificationChannel.valueOf(
                resultSet.getString("channel")
            ),
            resultSet.getString("title"),
            resultSet.getString("body"),
            resultSet.getString("payload_json"),
            NotificationStatus.valueOf(
                resultSet.getString("status")
            ),
            NotificationPriority.valueOf(
                resultSet.getString("priority")
            ),
            resultSet.getString("deduplication_key"),
            resultSet.getObject(
                "scheduled_at",
                OffsetDateTime.class
            ),
            resultSet.getInt("retry_count"),
            resultSet.getString("last_error"),
            resultSet.getObject(
                "sent_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "read_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            ),
            replayed
        );
    }

    private NotificationResponse replay(
        NotificationResponse stored
    ) {

        return new NotificationResponse(
            stored.id(),
            stored.userId(),
            stored.type(),
            stored.channel(),
            stored.title(),
            stored.body(),
            stored.payloadJson(),
            stored.status(),
            stored.priority(),
            stored.deduplicationKey(),
            stored.scheduledAt(),
            stored.retryCount(),
            stored.lastError(),
            stored.sentAt(),
            stored.readAt(),
            stored.createdAt(),
            true
        );
    }

    private static boolean samePostgresTimestamp(
        OffsetDateTime left,
        OffsetDateTime right
    ) {

        if (
            left == null
            || right == null
        ) {
            return left == right;
        }

        Duration delta =
            Duration.between(
                left.toInstant(),
                right.toInstant()
            ).abs();

        return delta.compareTo(
            Duration.ofNanos(
                1_000L
            )
        ) < 0;
    }
}