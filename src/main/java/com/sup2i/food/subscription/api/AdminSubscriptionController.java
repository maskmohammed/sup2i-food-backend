package com.sup2i.food.subscription.api;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.subscription.api.dto.ActivateSubscriptionRequest;
import com.sup2i.food.subscription.api.dto.SubscriptionActionRequest;
import com.sup2i.food.subscription.api.dto.SubscriptionMutationResponse;
import com.sup2i.food.subscription.api.dto.SubscriptionResponse;
import com.sup2i.food.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(
    name = "Admin Subscriptions",
    description = "Back-office subscription lifecycle: activation, suspension, cancellation, expiry."
)
@RestController
@RequestMapping("/api/v1/admin/subscriptions")
public class AdminSubscriptionController {

    private final SubscriptionService service;

    public AdminSubscriptionController(
        SubscriptionService service
    ) {
        this.service =
            service;
    }

    @GetMapping
    @PreAuthorize(
        "hasAuthority('subscription.read')"
    )
    public PageResponse<SubscriptionResponse> list(
        JwtAuthenticationToken authentication,

        @RequestParam(
            defaultValue = "0"
        )
        int page,

        @RequestParam(
            defaultValue = "20"
        )
        int size
    ) {

        return service.listByOrganization(
            userId(authentication),
            page,
            size
        );
    }

    @GetMapping("/{subscriptionId}")
    @PreAuthorize(
        "hasAuthority('subscription.read')"
    )
    public SubscriptionResponse find(
        @PathVariable UUID subscriptionId,
        JwtAuthenticationToken authentication
    ) {

        return service.findByOrganization(
            userId(authentication),
            subscriptionId
        );
    }

    @PostMapping("/{subscriptionId}/activate")
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public SubscriptionMutationResponse activate(
        @PathVariable UUID subscriptionId,
        @Valid
        @RequestBody
        ActivateSubscriptionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.activate(
            userId(authentication),
            subscriptionId,
            request
        );
    }

    @PostMapping("/{subscriptionId}/suspend")
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public SubscriptionMutationResponse suspend(
        @PathVariable UUID subscriptionId,
        @RequestBody(
            required = false
        )
        SubscriptionActionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.suspend(
            userId(authentication),
            subscriptionId,
            request == null
                ? null
                : request.reason()
        );
    }

    @PostMapping("/{subscriptionId}/reactivate")
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public SubscriptionMutationResponse reactivate(
        @PathVariable UUID subscriptionId,
        @RequestBody(
            required = false
        )
        SubscriptionActionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.reactivate(
            userId(authentication),
            subscriptionId,
            request == null
                ? null
                : request.reason()
        );
    }

    @PostMapping("/{subscriptionId}/cancel")
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public SubscriptionMutationResponse cancel(
        @PathVariable UUID subscriptionId,
        @RequestBody(
            required = false
        )
        SubscriptionActionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.cancel(
            userId(authentication),
            subscriptionId,
            request == null
                ? null
                : request.reason()
        );
    }

    @PostMapping("/expire-outdated")
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public int expireOutdated(
        JwtAuthenticationToken authentication
    ) {

        userId(authentication);

        return service.expireOutdated();
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