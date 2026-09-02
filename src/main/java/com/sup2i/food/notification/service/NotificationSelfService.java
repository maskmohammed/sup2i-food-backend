package com.sup2i.food.notification.service;

import com.sup2i.food.notification.api.dto.NotificationItemResponse;
import com.sup2i.food.notification.api.dto.PagedNotificationsResponse;
import com.sup2i.food.notification.domain.NotificationStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationSelfService {

    private static final int MAX_PAGE_SIZE =
        100;

    private final JdbcTemplate jdbcTemplate;

    private final NotificationService notificationService;

    public NotificationSelfService(
        JdbcTemplate jdbcTemplate,
        NotificationService notificationService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.notificationService =
            notificationService;
    }

    @Transactional(readOnly = true)
    public PagedNotificationsResponse list(
        UUID actorId,
        int page,
        int size
    ) {

        Actor actor =
            actor(
                actorId
            );

        int safePage =
            Math.max(
                page,
                0
            );

        int safeSize =
            Math.min(
                Math.max(
                    size,
                    1
                ),
                MAX_PAGE_SIZE
            );

        long offset =
            (long) safePage
                * safeSize;

        Long total =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notifications notification
                JOIN users user_account
                  ON user_account.id =
                     notification.user_id
                WHERE notification.user_id = ?
                  AND user_account.organization_id = ?
                """,
                Long.class,
                actor.userId(),
                actor.organizationId()
            );

        long totalElements =
            total == null
                ? 0L
                : total;

        List<NotificationItemResponse> content =
            jdbcTemplate.query(
                """
                SELECT
                    notification.id,
                    notification.type,
                    notification.title,
                    notification.body,
                    notification.status,
                    notification.created_at,
                    notification.read_at
                FROM notifications notification
                JOIN users user_account
                  ON user_account.id =
                     notification.user_id
                WHERE notification.user_id = ?
                  AND user_account.organization_id = ?
                ORDER BY
                    notification.created_at DESC,
                    notification.id DESC
                LIMIT ?
                OFFSET ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new NotificationItemResponse(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "type"
                        ),
                        resultSet.getString(
                            "title"
                        ),
                        resultSet.getString(
                            "body"
                        ),
                        NotificationStatus.valueOf(
                            resultSet.getString(
                                "status"
                            )
                        ),
                        resultSet.getObject(
                            "created_at",
                            java.time.OffsetDateTime.class
                        ),
                        resultSet.getObject(
                            "read_at",
                            java.time.OffsetDateTime.class
                        )
                    ),
                actor.userId(),
                actor.organizationId(),
                safeSize,
                offset
            );

        int totalPages =
            totalElements == 0L
                ? 0
                : (int) (
                    (
                        totalElements
                            + safeSize
                            - 1L
                    )
                        / safeSize
                );

        return new PagedNotificationsResponse(
            List.copyOf(
                content
            ),
            safePage,
            safeSize,
            totalElements,
            totalPages
        );
    }

    @Transactional
    public void markRead(
        UUID actorId,
        UUID notificationId
    ) {

        if (notificationId == null) {
            throw new IllegalArgumentException(
                "notificationId is required."
            );
        }

        Actor actor =
            actor(
                actorId
            );

        notificationService.markRead(
            actor.organizationId(),
            actor.userId(),
            notificationId
        );
    }

    private Actor actor(
        UUID actorId
    ) {

        if (actorId == null) {

            throw new BadCredentialsException(
                "Authenticated user identifier is missing."
            );
        }

        List<Actor> rows =
            jdbcTemplate.query(
                """
                SELECT
                    user_account.id,
                    user_account.organization_id
                FROM users user_account
                JOIN organizations organization
                  ON organization.id =
                     user_account.organization_id
                WHERE user_account.id = ?
                  AND user_account.status = 'ACTIVE'
                  AND organization.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new Actor(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "organization_id",
                            UUID.class
                        )
                    ),
                actorId
            );

        if (rows.isEmpty()) {

            throw new BadCredentialsException(
                "Authenticated user is inactive or does not exist."
            );
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Notification actor lookup returned multiple rows."
            );
        }

        return rows.get(0);
    }

    private record Actor(
        UUID userId,
        UUID organizationId
    ) {
    }
}