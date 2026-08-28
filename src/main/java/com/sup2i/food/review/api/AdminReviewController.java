package com.sup2i.food.review.api;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.review.api.dto.ModerateReviewRequest;
import com.sup2i.food.review.api.dto.ReviewResponse;
import com.sup2i.food.review.service.ReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin Reviews", description = "Back-office review moderation.")
@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {

    private final ReviewService service;

    public AdminReviewController(
        ReviewService service
    ) {
        this.service = service;
    }

    @GetMapping("/pending")
    @PreAuthorize(
        "hasAuthority('review.read')"
    )
    public PageResponse<ReviewResponse> pending(
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
        return service.pending(
            userId(authentication),
            page,
            size
        );
    }

    @PatchMapping("/{reviewId}/moderate")
    @PreAuthorize(
        "hasAuthority('review.write')"
    )
    public ReviewResponse moderate(
        @PathVariable UUID reviewId,
        @Valid
        @RequestBody
        ModerateReviewRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.moderate(
            userId(authentication),
            reviewId,
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
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(
                "Invalid JWT subject."
            );
        }
    }
}