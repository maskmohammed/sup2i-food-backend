package com.sup2i.food.notification.repository;

import com.sup2i.food.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository
    extends JpaRepository<Notification, UUID> {

    List<Notification>
        findAllByUser_IdOrderByCreatedAtDesc(
            UUID userId
        );

    Optional<Notification>
        findByIdAndUser_Id(
            UUID id,
            UUID userId
        );
}
