package com.sup2i.food.notification.service;

import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.notification.api.dto.CreateNotificationCommand;
import com.sup2i.food.notification.domain.NotificationChannel;
import com.sup2i.food.notification.domain.NotificationEventDefinition;
import com.sup2i.food.notification.domain.NotificationEventType;
import com.sup2i.food.order.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Dispatches source-backed mobile-order notification events.
 *
 * Recipient resolution happens while the canonical business
 * transaction is active. Persistence is registered through the
 * AFTER_COMMIT bridge and therefore does not participate in the
 * business transaction.
 *
 * Only message copy explicitly backed by the SUP2I FOOD product
 * specification is implemented here.
 */
@Service
public class OrderNotificationDispatchService {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            OrderNotificationDispatchService.class
        );

    private final NotificationEventCatalog eventCatalog;

    private final NotificationAfterCommitService afterCommitService;

    public OrderNotificationDispatchService(
        NotificationEventCatalog eventCatalog,
        NotificationAfterCommitService afterCommitService
    ) {

        this.eventCatalog =
            eventCatalog;

        this.afterCommitService =
            afterCommitService;
    }

    public void paymentConfirmedAfterCommit(
        Order order
    ) {

        dispatch(
            order,
            NotificationEventType.PAYMENT_CONFIRMED,
            "Commande payée",
            " est confirmée."
        );
    }

    public void orderReadyAfterCommit(
        Order order
    ) {

        dispatch(
            order,
            NotificationEventType.ORDER_READY,
            "Commande prête",
            " est prête."
        );
    }

    private void dispatch(
        Order order,
        NotificationEventType eventType,
        String title,
        String bodySuffix
    ) {

        try {

            if (order == null) {
                return;
            }

            UUID orderId =
                order.getId();

            if (orderId == null) {
                return;
            }

            String orderNumber =
                order.getOrderNumber();

            if (
                orderNumber == null
                || orderNumber.isBlank()
            ) {
                return;
            }

            Student student =
                order.getStudent();

            /*
             * Direct POS orders without a student are outside the
             * student mobile-notification flow.
             */
            if (student == null) {
                return;
            }

            User user =
                student.getUser();

            if (user == null) {
                return;
            }

            UUID userId =
                user.getId();

            if (userId == null) {
                return;
            }

            if (user.getOrganization() == null) {
                return;
            }

            UUID organizationId =
                user.getOrganization().getId();

            if (organizationId == null) {
                return;
            }

            NotificationEventDefinition definition =
                eventCatalog.get(
                    eventType
                );

            String eventIdentity =
                eventType.name()
                    + ":"
                    + orderId;

            String body =
                "Votre commande "
                    + orderNumber
                    + bodySuffix;

            for (
                NotificationChannel channel
                    : definition.requiredChannels()
            ) {

                dispatchChannelSafely(
                    organizationId,
                    userId,
                    orderId,
                    eventIdentity,
                    eventType,
                    channel,
                    title,
                    body,
                    definition
                );
            }

        } catch (RuntimeException exception) {

            /*
             * Notification preparation must never make a
             * Payment/Order/Kitchen transaction fail.
             */
            LOGGER.warn(
                "Unable to prepare {} notification dispatch",
                eventType,
                exception
            );
        }
    }

    private void dispatchChannelSafely(
        UUID organizationId,
        UUID userId,
        UUID orderId,
        String eventIdentity,
        NotificationEventType eventType,
        NotificationChannel channel,
        String title,
        String body,
        NotificationEventDefinition definition
    ) {

        try {

            String notificationIdentity =
                eventIdentity
                    + ":"
                    + channel.name();

            UUID notificationId =
                UUID.nameUUIDFromBytes(
                    notificationIdentity.getBytes(
                        StandardCharsets.UTF_8
                    )
                );

            CreateNotificationCommand command =
                new CreateNotificationCommand(
                    eventType.name(),
                    channel,
                    title,
                    body,
                    null,
                    definition.priority(),
                    eventIdentity,
                    null
                );

            afterCommitService.enqueueAfterCommit(
                organizationId,
                notificationId,
                userId,
                command
            );

        } catch (RuntimeException exception) {

            LOGGER.warn(
                "Unable to register {} notification for order {} on channel {}",
                eventType,
                orderId,
                channel,
                exception
            );
        }
    }
}