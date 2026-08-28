package com.sup2i.food.subscription.api;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.subscription.api.dto.CreatePlanRequest;
import com.sup2i.food.subscription.api.dto.SubscriptionPlanResponse;
import com.sup2i.food.subscription.service.SubscriptionPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(
    name = "Admin Subscription Plans",
    description = "Back-office management of subscription plans."
)
@RestController
@RequestMapping("/api/v1/admin/subscription-plans")
public class AdminSubscriptionPlanController {

    private final SubscriptionPlanService service;

    public AdminSubscriptionPlanController(
        SubscriptionPlanService service
    ) {
        this.service =
            service;
    }

    @PostMapping
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public SubscriptionPlanResponse create(
        @Valid
        @RequestBody
        CreatePlanRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.createPlan(
            userId(authentication),
            request
        );
    }

    @GetMapping
    @PreAuthorize(
        "hasAuthority('subscription.read')"
    )
    public PageResponse<SubscriptionPlanResponse> list(
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

        return service.listActivePlans(
            userId(authentication),
            page,
            size
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