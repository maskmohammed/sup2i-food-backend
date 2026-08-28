package com.sup2i.food.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.notification.api.dto.NotificationMutationResponse;
import com.sup2i.food.notification.api.dto.NotificationResponse;
import com.sup2i.food.notification.domain.Notification;
import com.sup2i.food.notification.domain.NotificationChannel;
import com.sup2i.food.notification.domain.NotificationStatus;
import com.sup2i.food.notification.domain.NotificationType;
import com.sup2i.food.notification.exception.NotificationNotFoundException;
import com.sup2i.food.notification.repository.NotificationRepository;
import com.sup2i.food.order.domain.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper =
        new ObjectMapper();

    public NotificationService(
        NotificationRepository notificationRepository,
        UserRepository userRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.notificationRepository =
            notificationRepository;

        this.userRepository =
            userRepository;

        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public void notifyOrderQueued(
        Order order
    ) {

        create(
            order,
            NotificationType.ORDER_QUEUED,
            "Commande reçue",
            "Votre commande "
                + order.getOrderNumber()
                + " a été reçue et payée, elle est en préparation."
        );
    }

    @Transactional
    public void notifyOrderReady(
        Order order
    ) {

        create(
            order,
            NotificationType.ORDER_READY,
            "Commande prête",
            "Votre commande "
                + order.getOrderNumber()
                + " est prête, vous pouvez venir la récupérer."
        );
    }

    private void create(
        Order order,
        NotificationType type,
        String title,
        String body
    ) {

        User recipient =
            order.getStudent()
                .getUser();

        if (
            !transactionalEnabled(
                recipient.getId()
            )
        ) {
            return;
        }

        Notification notification =
            new Notification(
                recipient,
                type,
                NotificationChannel.PUSH,
                title,
                body,
                payload(order)
            );

        notificationRepository
            .save(notification);
    }

    private boolean transactionalEnabled(
        UUID userId
    ) {

        List<Boolean> rows =
            jdbcTemplate.query(
                """
                SELECT push_enabled
                FROM notification_preferences
                WHERE user_id = ?
                  AND category = 'TRANSACTIONAL'
                """,
                (resultSet, rowNum) ->
                    resultSet.getBoolean(
                        "push_enabled"
                    ),
                userId
            );

        return rows.isEmpty()
            || rows.get(0);
    }

    private String payload(
        Order order
    ) {

        Map<String, String> data =
            new LinkedHashMap<>();

        data.put(
            "orderId",
            order.getId()
                .toString()
        );

        data.put(
            "orderNumber",
            order.getOrderNumber()
        );

        try {

            return objectMapper
                .writeValueAsString(
                    data
                );

        } catch (
            Exception exception
        ) {
            throw new IllegalStateException(
                "Unable to serialize notification payload.",
                exception
            );
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(
        UUID actorId
    ) {

        User actor =
            resolveActor(actorId);

        return notificationRepository
            .findAllByUser_IdOrderByCreatedAtDesc(
                actor.getId()
            )
            .stream()
            .map(this::response)
            .toList();
    }

    @Transactional
    public NotificationMutationResponse markRead(
        UUID actorId,
        UUID notificationId
    ) {

        User actor =
            resolveActor(actorId);

        Notification notification =
            notificationRepository
                .findByIdAndUser_Id(
                    notificationId,
                    actor.getId()
                )
                .orElseThrow(() ->
                    new NotificationNotFoundException(
                        "Notification does not exist."
                    )
                );

        if (
            notification.getStatus()
                == NotificationStatus.READ
        ) {
            return new NotificationMutationResponse(
                response(notification),
                true
            );
        }

        notification.markRead(
            OffsetDateTime.now()
        );

        notificationRepository
            .saveAndFlush(notification);

        return new NotificationMutationResponse(
            response(notification),
            false
        );
    }

    private User resolveActor(
        UUID actorId
    ) {

        return userRepository
            .findById(actorId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private NotificationResponse response(
        Notification notification
    ) {

        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getChannel(),
            notification.getTitle(),
            notification.getBody(),
            notification.getPayload(),
            notification.getStatus(),
            notification.getSentAt(),
            notification.getReadAt(),
            notification.getCreatedAt()
        );
    }
}
