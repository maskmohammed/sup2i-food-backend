package com.sup2i.food.promotion.api;

import com.sup2i.food.promotion.api.dto.LoyaltyBalanceResponse;
import com.sup2i.food.promotion.api.dto.LoyaltyRedeemRequest;
import com.sup2i.food.promotion.api.dto.LoyaltyRedeemResponse;
import com.sup2i.food.promotion.service.LoyaltyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Loyalty", description = "Student loyalty points balance and redemption.")
@RestController
@RequestMapping("/api/v1/loyalty")
@PreAuthorize("isAuthenticated()")
public class LoyaltyController {

    private final LoyaltyService service;

    public LoyaltyController(
        LoyaltyService service
    ) {
        this.service =
            service;
    }

    @GetMapping("/balance")
    public LoyaltyBalanceResponse balance(
        JwtAuthenticationToken authentication
    ) {

        return service.getBalance(
            userId(authentication)
        );
    }

    @PostMapping("/redeem")
    public LoyaltyRedeemResponse redeem(
        @Valid
        @RequestBody
        LoyaltyRedeemRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.redeem(
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