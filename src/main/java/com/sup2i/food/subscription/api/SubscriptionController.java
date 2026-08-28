package com.sup2i.food.subscription.api;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.subscription.api.dto.SubscribeRequest;
import com.sup2i.food.subscription.api.dto.SubscriptionActionRequest;
import com.sup2i.food.subscription.api.dto.SubscriptionMutationResponse;
import com.sup2i.food.subscription.api.dto.SubscriptionPlanResponse;
import com.sup2i.food.subscription.api.dto.SubscriptionResponse;
import com.sup2i.food.subscription.api.dto.SubscriptionStatusHistoryResponse;
import com.sup2i.food.subscription.service.SubscriptionPlanService;
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

import java.util.List;
import java.util.UUID;

@Tag(
    name = "Subscriptions",
    description = "Student self-service: browse plans, subscribe, and manage own subscriptions."
)
@RestController
@RequestMapping("/api/v1/subscriptions")
@PreAuthorize("isAuthenticated()")
public class SubscriptionController {

    private final SubscriptionPlanService planService;
    private final SubscriptionService subscriptionService;

    public SubscriptionController(
        SubscriptionPlanService planService,
        SubscriptionService subscriptionService
    ) {
        this.planService =
            planService;

        this.subscriptionService =
            subscriptionService;
    }

    @GetMapping("/plans")
    public PageResponse<SubscriptionPlanResponse> plans(
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

        return planService.listActivePlans(
            userId(authentication),
            page,
            size
        );
    }

    @PostMapping
    public SubscriptionMutationResponse subscribe(
        @Valid
        @RequestBody
        SubscribeRequest request,
        JwtAuthenticationToken authentication
    ) {

        return subscriptionService.subscribe(
            userId(authentication),
            request
        );
    }

    @GetMapping
    public List<SubscriptionResponse> mySubscriptions(
        JwtAuthenticationToken authentication
    ) {

        return subscriptionService.listOwned(
            userId(authentication)
        );
    }

    @GetMapping("/{subscriptionId}")
    public SubscriptionResponse find(
        @PathVariable UUID subscriptionId,
        JwtAuthenticationToken authentication
    ) {

        return subscriptionService.findOwned(
            userId(authentication),
            subscriptionId
        );
    }

    @PostMapping("/{subscriptionId}/cancel")
    public SubscriptionMutationResponse cancel(
        @PathVariable UUID subscriptionId,
        @RequestBody(
            required = false
        )
        SubscriptionActionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return subscriptionService.cancelOwn(
            userId(authentication),
            subscriptionId,
            request == null
                ? null
                : request.reason()
        );
    }

    @GetMapping("/{subscriptionId}/history")
    public List<SubscriptionStatusHistoryResponse> history(
        @PathVariable UUID subscriptionId,
        JwtAuthenticationToken authentication
    ) {

        return subscriptionService.history(
            userId(authentication),
            subscriptionId
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