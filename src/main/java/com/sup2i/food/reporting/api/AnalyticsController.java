package com.sup2i.food.reporting.api;

import com.sup2i.food.reporting.api.dto.AnalyticsOverviewResponse;
import com.sup2i.food.reporting.service.AnalyticsOverviewService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsOverviewService
        analyticsOverviewService;

    public AnalyticsController(
        AnalyticsOverviewService analyticsOverviewService
    ) {
        this.analyticsOverviewService =
            analyticsOverviewService;
    }

    @GetMapping("/overview")
    @PreAuthorize(
        "hasAuthority('analytics.read')"
    )
    public AnalyticsOverviewResponse overview(
        JwtAuthenticationToken authentication,

        @RequestParam(
            required = false
        )
        UUID campusId,

        @RequestParam
        @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        )
        OffsetDateTime from,

        @RequestParam
        @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        )
        OffsetDateTime to
    ) {

        return analyticsOverviewService.overview(
            actorId(
                authentication
            ),
            campusId,
            from,
            to
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