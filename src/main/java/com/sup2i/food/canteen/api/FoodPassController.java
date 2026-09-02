package com.sup2i.food.canteen.api;

import com.sup2i.food.canteen.api.dto.FoodPassResponse;
import com.sup2i.food.canteen.service.FoodPassApplicationService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1")
public class FoodPassController {

    private final FoodPassApplicationService
        foodPassService;

    public FoodPassController(
        FoodPassApplicationService foodPassService
    ) {
        this.foodPassService =
            foodPassService;
    }

    @GetMapping("/me/food-pass")
    @PreAuthorize(
        "hasAuthority('foodpass.read')"
    )
    public FoodPassResponse mine(
        JwtAuthenticationToken authentication
    ) {

        return foodPassService.mine(
            actorId(
                authentication
            )
        );
    }

    @PostMapping(
        "/food-passes/{foodPassId}/report-lost"
    )
    @PreAuthorize(
        "hasAuthority('foodpass.report_lost')"
    )
    public FoodPassResponse reportLost(
        JwtAuthenticationToken authentication,

        @PathVariable
        UUID foodPassId,

        @NotBlank
        @Size(
            min = 8,
            max = 160
        )
        @RequestHeader(
            name = "Idempotency-Key",
            required = true
        )
        String idempotencyKey
    ) {

        return foodPassService.reportLost(
            actorId(
                authentication
            ),
            idempotencyKey,
            foodPassId
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