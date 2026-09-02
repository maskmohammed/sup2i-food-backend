package com.sup2i.food.subscription.api;

import com.sup2i.food.subscription.api.dto.SubscriptionResponse;
import com.sup2i.food.subscription.service.SubscriptionReadService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SubscriptionController {

    private final SubscriptionReadService
        subscriptionReadService;

    public SubscriptionController(
        SubscriptionReadService subscriptionReadService
    ) {
        this.subscriptionReadService =
            subscriptionReadService;
    }

    @GetMapping("/me/subscriptions")
    @PreAuthorize(
        "hasAuthority('subscription.read')"
    )
    public List<SubscriptionResponse> mine(
        JwtAuthenticationToken authentication
    ) {

        return subscriptionReadService.mine(
            actorId(
                authentication
            )
        );
    }

    private UUID actorId(
        JwtAuthenticationToken authentication
    ) {

        try {

            return UUID.fromString(
                authentication
                    .getToken()
                    .getSubject()
            );
        }
        catch (
            IllegalArgumentException
            | NullPointerException exception
        ) {

            throw new BadCredentialsException(
                "Invalid authenticated user identifier.",
                exception
            );
        }
    }
}