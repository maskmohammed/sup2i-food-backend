package com.sup2i.food.promotion.api;

import com.sup2i.food.promotion.api.dto.PromotionResponse;
import com.sup2i.food.promotion.service.PromotionService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController {

    private final PromotionService service;

    public PromotionController(
        PromotionService service
    ) {
        this.service =
            service;
    }

    @GetMapping("/active")
    @PreAuthorize(
        "hasAuthority('promotion.read')"
    )
    public List<PromotionResponse> active(
        JwtAuthenticationToken authentication,

        @RequestParam
        UUID locationId
    ) {

        return service.listActive(
            actorId(authentication),
            locationId
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
