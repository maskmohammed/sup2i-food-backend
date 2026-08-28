package com.sup2i.food.review.api;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.review.api.dto.CreateReviewRequest;
import com.sup2i.food.review.api.dto.ReviewResponse;
import com.sup2i.food.review.service.ReviewService;
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

@Tag(name = "Reviews", description = "Student reviews: submit a review on a product or a delivered order.")
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(
        ReviewService service
    ) {
        this.service = service;
    }

    @PostMapping("/reviews")
    @PreAuthorize("isAuthenticated()")
    public ReviewResponse create(
        @Valid
        @RequestBody
        CreateReviewRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.create(
            userId(authentication),
            request
        );
    }

    @GetMapping("/products/{id}/reviews")
    public PageResponse<ReviewResponse> productReviews(
        @PathVariable UUID id,

        @RequestParam(
            defaultValue = "0"
        )
        int page,

        @RequestParam(
            defaultValue = "20"
        )
        int size
    ) {
        return service.listProductReviews(
            id,
            page,
            size
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