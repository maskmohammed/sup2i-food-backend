package com.sup2i.food.notification.api;

import com.sup2i.food.notification.api.dto.NotificationMutationResponse;
import com.sup2i.food.notification.api.dto.NotificationResponse;
import com.sup2i.food.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(
    name = "Notifications",
    description = "Mobile-facing in-app notifications: list and mark as read."
)
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(
        NotificationService service
    ) {
        this.service =
            service;
    }

    @GetMapping
    public List<NotificationResponse> list(
        JwtAuthenticationToken authentication
    ) {

        return service.list(
            userId(authentication)
        );
    }

    @PostMapping("/{notificationId}/read")
    public NotificationMutationResponse markRead(
        @PathVariable UUID notificationId,
        JwtAuthenticationToken authentication
    ) {

        return service.markRead(
            userId(authentication),
            notificationId
        );
    }

    private UUID userId(
        JwtAuthenticationToken authentication
    ) {

        try {

            return UUID.fromString(
                authentication
                    .getToken()
                    .getSubject()
            );

        } catch (
            IllegalArgumentException exception
        ) {

            throw new BadCredentialsException(
                "Invalid JWT subject."
            );
        }
    }
}
