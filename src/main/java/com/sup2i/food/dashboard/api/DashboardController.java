package com.sup2i.food.dashboard.api;

import com.sup2i.food.dashboard.api.dto.DashboardSummaryResponse;
import com.sup2i.food.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(
    name = "Dashboard",
    description = "Executive dashboard: revenue, order KPIs, top products, kitchen performance. Direction role required."
)
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasRole('DIRECTION')")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(
        DashboardService service
    ) {
        this.service =
            service;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
        JwtAuthenticationToken authentication
    ) {

        return service.summary(
            userId(authentication)
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
