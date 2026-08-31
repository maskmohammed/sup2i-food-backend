package com.sup2i.food.notification.service;

import com.sup2i.food.notification.api.dto.CreateNotificationCommand;
import com.sup2i.food.notification.api.dto.NotificationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Starts an independent notification transaction.
 *
 * This bean exists separately from NotificationAfterCommitService so
 * Spring transaction interception applies when invoked after a business
 * transaction has committed.
 */
@Service
public class NotificationPersistenceService {

    private final NotificationService notificationService;

    public NotificationPersistenceService(
        NotificationService notificationService
    ) {
        this.notificationService =
            notificationService;
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public NotificationResponse persist(
        UUID organizationId,
        UUID notificationId,
        UUID userId,
        CreateNotificationCommand command
    ) {

        return notificationService.enqueue(
            organizationId,
            notificationId,
            userId,
            command
        );
    }
}