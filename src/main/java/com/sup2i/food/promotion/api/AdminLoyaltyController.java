package com.sup2i.food.promotion.api;

import com.sup2i.food.promotion.api.dto.AdminLoyaltyAdjustRequest;
import com.sup2i.food.promotion.api.dto.LoyaltyAdjustResponse;
import com.sup2i.food.promotion.service.LoyaltyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin Loyalty", description = "Back-office loyalty balance adjustments.")
@RestController
@RequestMapping("/api/v1/admin/loyalty")
public class AdminLoyaltyController {

    private final LoyaltyService service;

    public AdminLoyaltyController(
        LoyaltyService service
    ) {
        this.service =
            service;
    }

    @PostMapping("/adjust")
    @PreAuthorize(
        "hasAuthority('loyalty.write')"
    )
    public LoyaltyAdjustResponse adjust(
        @Valid
        @RequestBody
        AdminLoyaltyAdjustRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.adjustByAdmin(
            userId(authentication),
            request
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