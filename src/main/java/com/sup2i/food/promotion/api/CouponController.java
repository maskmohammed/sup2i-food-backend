package com.sup2i.food.promotion.api;

import com.sup2i.food.promotion.api.dto.ApplyCouponRequest;
import com.sup2i.food.promotion.api.dto.ApplyCouponResponse;
import com.sup2i.food.promotion.api.dto.CouponValidationRequest;
import com.sup2i.food.promotion.api.dto.CouponValidationResponse;
import com.sup2i.food.promotion.service.CouponService;
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

@Tag(name = "Coupons", description = "Student coupon validation and application on draft orders.")
@RestController
@RequestMapping("/api/v1/coupons")
@PreAuthorize("isAuthenticated()")
public class CouponController {

    private final CouponService service;

    public CouponController(
        CouponService service
    ) {
        this.service =
            service;
    }

    @PostMapping("/validate")
    public CouponValidationResponse validate(
        @Valid
        @RequestBody
        CouponValidationRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.validate(
            userId(authentication),
            request
        );
    }

    @PostMapping("/apply")
    public ApplyCouponResponse apply(
        @Valid
        @RequestBody
        ApplyCouponRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.apply(
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