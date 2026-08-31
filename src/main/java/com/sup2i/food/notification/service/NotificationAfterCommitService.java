package com.sup2i.food.notification.service;

import com.sup2i.food.notification.api.dto.CreateNotificationCommand;
import com.sup2i.food.notification.exception.NotificationValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Safely separates business transactions from notification persistence.
 *
 * When called inside a synchronized Spring transaction, notification
 * persistence is registered for AFTER COMMIT and executes in an
 * independent transaction.
 *
 * The bridge deliberately performs no external PUSH/EMAIL call.
 *
 * Without a durable outbox, a JVM crash between the business commit
 * and the callback may lose the notification. This class therefore
 * provides transaction isolation, not crash-durable messaging.
 */
@Service
public class NotificationAfterCommitService {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            NotificationAfterCommitService.class
        );

    private final NotificationPersistenceService persistenceService;

    public NotificationAfterCommitService(
        NotificationPersistenceService persistenceService
    ) {
        this.persistenceService =
            persistenceService;
    }

    public void enqueueAfterCommit(
        UUID organizationId,
        UUID notificationId,
        UUID userId,
        CreateNotificationCommand command
    ) {

        validate(
            organizationId,
            notificationId,
            userId,
            command
        );

        boolean transactionActive =
            TransactionSynchronizationManager
                .isActualTransactionActive();

        boolean synchronizationActive =
            TransactionSynchronizationManager
                .isSynchronizationActive();

        if (
            transactionActive
            && synchronizationActive
        ) {

            registerAfterCommit(
                organizationId,
                notificationId,
                userId,
                command
            );

            return;
        }

        if (transactionActive) {

            /*
             * Persisting before commit could create a ghost notification
             * if the business transaction subsequently rolls back.
             * Skip safely instead of contaminating the business outcome.
             */
            LOGGER.warn(
                "Notification {} not registered because transaction synchronization is unavailable",
                notificationId
            );

            return;
        }

        /*
         * Outside a business transaction there is nothing to wait for.
         * The independent persistence transaction may execute directly.
         */
        persistenceService.persist(
            organizationId,
            notificationId,
            userId,
            command
        );
    }

    private void registerAfterCommit(
        UUID organizationId,
        UUID notificationId,
        UUID userId,
        CreateNotificationCommand command
    ) {

        try {

            TransactionSynchronizationManager
                .registerSynchronization(
                    new TransactionSynchronization() {

                        @Override
                        public void afterCommit() {

                            persistSafely(
                                organizationId,
                                notificationId,
                                userId,
                                command
                            );
                        }
                    }
                );

        } catch (RuntimeException exception) {

            /*
             * Registration failure must not make notification
             * infrastructure capable of rolling back business work.
             */
            LOGGER.warn(
                "Unable to register post-commit notification {}",
                notificationId,
                exception
            );
        }
    }

    private void persistSafely(
        UUID organizationId,
        UUID notificationId,
        UUID userId,
        CreateNotificationCommand command
    ) {

        try {

            persistenceService.persist(
                organizationId,
                notificationId,
                userId,
                command
            );

        } catch (RuntimeException exception) {

            /*
             * Business transaction is already committed here.
             * Record the infrastructure failure without propagating it
             * back into the business lifecycle.
             */
            LOGGER.warn(
                "Post-commit notification persistence failed for {}",
                notificationId,
                exception
            );
        }
    }

    private void validate(
        UUID organizationId,
        UUID notificationId,
        UUID userId,
        CreateNotificationCommand command
    ) {

        if (organizationId == null) {
            throw new NotificationValidationException(
                "Organization id is required."
            );
        }

        if (notificationId == null) {
            throw new NotificationValidationException(
                "Notification id is required."
            );
        }

        if (userId == null) {
            throw new NotificationValidationException(
                "Notification target user id is required."
            );
        }

        if (command == null) {
            throw new NotificationValidationException(
                "Notification command is required."
            );
        }
    }
}