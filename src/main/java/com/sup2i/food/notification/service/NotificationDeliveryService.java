package com.sup2i.food.notification.service;

import com.sup2i.food.notification.api.dto.NotificationResponse;
import com.sup2i.food.notification.domain.NotificationChannel;
import com.sup2i.food.notification.domain.NotificationPriority;
import com.sup2i.food.notification.domain.NotificationStatus;
import com.sup2i.food.notification.exception.NotificationConflictException;
import com.sup2i.food.notification.exception.NotificationNotFoundException;
import com.sup2i.food.notification.exception.NotificationValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class NotificationDeliveryService {

    private static final int MAX_DUE_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public NotificationDeliveryService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> due(
        UUID organizationId,
        int limit
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        int normalizedLimit =
            normalizeLimit(
                limit
            );

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
            WHERE u.organization_id = ?
              AND n.status = 'PENDING'
              AND (
                    n.scheduled_at IS NULL
                    OR n.scheduled_at <= CURRENT_TIMESTAMP
              )
            ORDER BY
                CASE n.priority
                    WHEN 'CRITICAL' THEN 1
                    WHEN 'HIGH' THEN 2
                    WHEN 'NORMAL' THEN 3
                    WHEN 'LOW' THEN 4
                    ELSE 5
                END,
                COALESCE(
                    n.scheduled_at,
                    n.created_at
                ) ASC,
                n.created_at ASC,
                n.id ASC
            LIMIT ?
            """,
            (resultSet, rowNumber) ->
                map(
                    resultSet,
                    false
                ),
            organizationId,
            normalizedLimit
        );
    }

    @Transactional
    public NotificationResponse markSent(
        UUID organizationId,
        UUID notificationId
    ) {

        NotificationResponse stored =
            locked(
                organizationId,
                notificationId
            );

        if (
            stored.status() == NotificationStatus.SENT
            || stored.status() == NotificationStatus.READ
        ) {
            return replay(stored);
        }

        if (stored.status() != NotificationStatus.PENDING) {
            throw new NotificationConflictException(
                "Only a PENDING notification can be marked SENT."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE notifications
                SET
                    status = 'SENT',
                    sent_at = CURRENT_TIMESTAMP,
                    last_error = NULL
                WHERE id = ?
                  AND status = 'PENDING'
                """,
                notificationId
            );

        if (updated != 1) {
            throw new NotificationConflictException(
                "Notification changed concurrently."
            );
        }

        return required(
            organizationId,
            notificationId
        );
    }

    @Transactional
    public NotificationResponse markFailed(
        UUID organizationId,
        UUID notificationId,
        String rawError
    ) {

        String error =
            normalizeError(
                rawError
            );

        NotificationResponse stored =
            locked(
                organizationId,
                notificationId
            );

        if (stored.status() == NotificationStatus.FAILED) {

            if (
                Objects.equals(
                    stored.lastError(),
                    error
                )
            ) {
                return replay(stored);
            }

            throw new NotificationConflictException(
                "Notification is already FAILED with another delivery error."
            );
        }

        if (stored.status() != NotificationStatus.PENDING) {
            throw new NotificationConflictException(
                "Only a PENDING notification can be marked FAILED."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE notifications
                SET
                    status = 'FAILED',
                    retry_count = retry_count + 1,
                    last_error = ?
                WHERE id = ?
                  AND status = 'PENDING'
                """,
                error,
                notificationId
            );

        if (updated != 1) {
            throw new NotificationConflictException(
                "Notification changed concurrently."
            );
        }

        return required(
            organizationId,
            notificationId
        );
    }

    @Transactional
    public NotificationResponse retry(
        UUID organizationId,
        UUID notificationId,
        OffsetDateTime scheduledAt
    ) {

        NotificationResponse stored =
            locked(
                organizationId,
                notificationId
            );

        if (stored.status() == NotificationStatus.PENDING) {

            if (
                samePostgresTimestamp(
                    stored.scheduledAt(),
                    scheduledAt
                )
            ) {
                return replay(stored);
            }

            throw new NotificationConflictException(
                "Notification is already PENDING with another schedule."
            );
        }

        if (stored.status() != NotificationStatus.FAILED) {
            throw new NotificationConflictException(
                "Only a FAILED notification can be retried."
            );
        }

        ensureNoActiveDeduplicationConflict(
            organizationId,
            stored
        );

        int updated =
            jdbcTemplate.update(
                """
                UPDATE notifications
                SET
                    status = 'PENDING',
                    scheduled_at = ?,
                    last_error = NULL,
                    sent_at = NULL
                WHERE id = ?
                  AND status = 'FAILED'
                """,
                scheduledAt,
                notificationId
            );

        if (updated != 1) {
            throw new NotificationConflictException(
                "Notification changed concurrently."
            );
        }

        return required(
            organizationId,
            notificationId
        );
    }

    private void ensureNoActiveDeduplicationConflict(
        UUID organizationId,
        NotificationResponse stored
    ) {

        if (stored.deduplicationKey() == null) {
            return;
        }

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notifications n
                JOIN users u
                  ON u.id = n.user_id
                WHERE u.organization_id = ?
                  AND n.user_id = ?
                  AND n.channel = ?
                  AND n.deduplication_key = ?
                  AND n.status IN ('PENDING','SENT')
                  AND n.id <> ?
                """,
                Integer.class,
                organizationId,
                stored.userId(),
                stored.channel().name(),
                stored.deduplicationKey(),
                stored.id()
            );

        if (
            count != null
            && count > 0
        ) {
            throw new NotificationConflictException(
                "Retry conflicts with an active deduplicated notification."
            );
        }
    }

    private NotificationResponse locked(
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

        List<NotificationResponse> rows =
            jdbcTemplate.query(
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
                FOR UPDATE OF n
                """,
                (resultSet, rowNumber) ->
                    map(
                        resultSet,
                        false
                    ),
                notificationId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new NotificationNotFoundException(
                "Notification does not exist."
            );
        }

        return rows.get(0);
    }

    private NotificationResponse required(
        UUID organizationId,
        UUID notificationId
    ) {

        List<NotificationResponse> rows =
            jdbcTemplate.query(
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
                """,
                (resultSet, rowNumber) ->
                    map(
                        resultSet,
                        false
                    ),
                notificationId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new NotificationNotFoundException(
                "Notification does not exist."
            );
        }

        return rows.get(0);
    }

    private int normalizeLimit(
        int limit
    ) {

        if (limit <= 0) {
            throw new NotificationValidationException(
                "Delivery list limit must be positive."
            );
        }

        return Math.min(
            limit,
            MAX_DUE_LIMIT
        );
    }

    private String normalizeError(
        String value
    ) {

        if (value == null) {
            throw new NotificationValidationException(
                "Delivery failure error is required."
            );
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {
            throw new NotificationValidationException(
                "Delivery failure error is required."
            );
        }

        return normalized;
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