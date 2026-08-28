package com.sup2i.food.promotion.api;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.promotion.api.dto.CouponResponse;
import com.sup2i.food.promotion.api.dto.CreateCouponRequest;
import com.sup2i.food.promotion.api.dto.UpdateCouponRequest;
import com.sup2i.food.promotion.service.CouponService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin Coupons", description = "Back-office coupon management: create, edit, deactivate, list.")
@RestController
@RequestMapping("/api/v1/admin/coupons")
public class AdminCouponController {

    private final CouponService service;

    public AdminCouponController(
        CouponService service
    ) {
        this.service =
            service;
    }

    @GetMapping
    @PreAuthorize(
        "hasAuthority('promotion.read')"
    )
    public PageResponse<CouponResponse> list(
        JwtAuthenticationToken authentication,

        @RequestParam(
            defaultValue = "0"
        )
        int page,

        @RequestParam(
            defaultValue = "20"
        )
        int size
    ) {

        return service.listByAdmin(
            userId(authentication),
            page,
            size
        );
    }

    @GetMapping("/{couponId}")
    @PreAuthorize(
        "hasAuthority('promotion.read')"
    )
    public CouponResponse find(
        @PathVariable UUID couponId,
        JwtAuthenticationToken authentication
    ) {

        return service.getByAdmin(
            userId(authentication),
            couponId
        );
    }

    @PostMapping
    @PreAuthorize(
        "hasAuthority('promotion.write')"
    )
    public CouponResponse create(
        @Valid
        @RequestBody
        CreateCouponRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.createByAdmin(
            userId(authentication),
            request
        );
    }

    @PostMapping("/{couponId}/update")
    @PreAuthorize(
        "hasAuthority('promotion.write')"
    )
    public CouponResponse update(
        @PathVariable UUID couponId,
        @Valid
        @RequestBody
        UpdateCouponRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.updateByAdmin(
            userId(authentication),
            couponId,
            request
        );
    }

    @PostMapping("/{couponId}/deactivate")
    @PreAuthorize(
        "hasAuthority('promotion.write')"
    )
    public CouponResponse deactivate(
        @PathVariable UUID couponId,
        JwtAuthenticationToken authentication
    ) {

        return service.deactivateByAdmin(
            userId(authentication),
            couponId
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