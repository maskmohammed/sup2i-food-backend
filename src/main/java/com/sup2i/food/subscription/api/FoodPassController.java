package com.sup2i.food.subscription.api;

import com.sup2i.food.subscription.api.dto.FoodPassResponse;
import com.sup2i.food.subscription.service.FoodPassService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(
    name = "Food Pass",
    description = "Student food pass card."
)
@RestController
@RequestMapping("/api/v1/food-pass")
@PreAuthorize("isAuthenticated()")
public class FoodPassController {

    private final FoodPassService service;

    public FoodPassController(
        FoodPassService service
    ) {
        this.service =
            service;
    }

    @GetMapping
    public FoodPassResponse myFoodPass(
        JwtAuthenticationToken authentication
    ) {

        return service.myFoodPass(
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