package com.sup2i.food.loyalty.api;

import com.sup2i.food.loyalty.api.dto.LoyaltyResponse;
import com.sup2i.food.loyalty.service.LoyaltyService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
public class LoyaltyController {

    private final LoyaltyService service;

    public LoyaltyController(
        LoyaltyService service
    ) {
        this.service =
            service;
    }

    @GetMapping("/loyalty")
    @PreAuthorize(
        "hasAuthority('loyalty.read')"
    )
    public LoyaltyResponse loyalty(
        JwtAuthenticationToken authentication
    ) {

        return service.getMyLoyalty(
            actorId(authentication)
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
