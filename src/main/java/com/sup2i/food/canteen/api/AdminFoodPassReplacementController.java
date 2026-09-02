package com.sup2i.food.canteen.api;

import com.sup2i.food.canteen.api.dto.FoodPassReplacementResponse;
import com.sup2i.food.canteen.service.FoodPassReplacementService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping(
    "/api/v1/admin/food-passes"
)
public class AdminFoodPassReplacementController {

    private final FoodPassReplacementService service;

    public AdminFoodPassReplacementController(
        FoodPassReplacementService service
    ) {
        this.service =
            service;
    }

    @PostMapping(
        "/{foodPassId}/replace"
    )
    @ResponseStatus(
        HttpStatus.CREATED
    )
    @PreAuthorize(
        "hasAuthority('foodpass.replace')"
    )
    public FoodPassReplacementResponse replace(
        JwtAuthenticationToken authentication,

        @PathVariable
        UUID foodPassId,

        @RequestHeader(
            name = "Idempotency-Key",
            required = true
        )
        @NotBlank
        @Size(
            min = 8,
            max = 160
        )
        String idempotencyKey
    ) {
        return service.replace(
            actorId(
                authentication
            ),
            foodPassId,
            idempotencyKey
        );
    }

    private UUID actorId(
        JwtAuthenticationToken authentication
    ) {
        if (
            authentication == null
            || authentication.getToken() == null
            || authentication.getToken().getSubject() == null
        ) {
            throw new BadCredentialsException(
                "Authenticated user identifier is missing."
            );
        }

        try {
            return UUID.fromString(
                authentication
                    .getToken()
                    .getSubject()
            );
        }
        catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(
                "Authenticated user identifier is invalid.",
                exception
            );
        }
    }
}