package com.sup2i.food.subscription.api;

import com.sup2i.food.subscription.api.dto.AdminSubscriptionPlanResponse;
import com.sup2i.food.subscription.api.dto.AdminSubscriptionResponse;
import com.sup2i.food.subscription.api.dto.CreateSubscriptionCommand;
import com.sup2i.food.subscription.api.dto.CreateSubscriptionPlanCommand;
import com.sup2i.food.subscription.service.SubscriptionAdminCommandService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping(
    "/api/v1/admin"
)
public class SubscriptionAdminCommandController {

    private final SubscriptionAdminCommandService service;

    public SubscriptionAdminCommandController(
        SubscriptionAdminCommandService service
    ) {
        this.service =
            service;
    }

    @PostMapping(
        "/subscription-plans"
    )
    @ResponseStatus(
        HttpStatus.CREATED
    )
    @PreAuthorize(
        "hasAuthority('subscription.create')"
    )
    public AdminSubscriptionPlanResponse createPlan(
        JwtAuthenticationToken authentication,

        @Valid
        @RequestBody
        CreateSubscriptionPlanCommand command
    ) {
        return service.createPlan(
            actorId(
                authentication
            ),
            command
        );
    }

    @PostMapping(
        "/subscriptions"
    )
    @ResponseStatus(
        HttpStatus.CREATED
    )
    @PreAuthorize(
        "hasAuthority('subscription.create')"
    )
    public AdminSubscriptionResponse createSubscription(
        JwtAuthenticationToken authentication,

        @Valid
        @RequestBody
        CreateSubscriptionCommand command
    ) {
        return service.createSubscription(
            actorId(
                authentication
            ),
            command
        );
    }

    private UUID actorId(
        JwtAuthenticationToken authentication
    ) {
        if (
            authentication == null
            || authentication.getToken() == null
            || authentication.getToken().getSubject() == null
        ) {
            throw new BadCredentialsException(
                "Authenticated user identifier is missing."
            );
        }

        try {
            return UUID.fromString(
                authentication
                    .getToken()
                    .getSubject()
            );
        }
        catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(
                "Authenticated user identifier is invalid.",
                exception
            );
        }
    }
}