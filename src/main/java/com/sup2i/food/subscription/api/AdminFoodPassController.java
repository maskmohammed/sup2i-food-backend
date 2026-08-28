package com.sup2i.food.subscription.api;

import com.sup2i.food.subscription.api.dto.ConsumeMealRequest;
import com.sup2i.food.subscription.api.dto.FoodPassActionRequest;
import com.sup2i.food.subscription.api.dto.FoodPassEventResponse;
import com.sup2i.food.subscription.api.dto.FoodPassResponse;
import com.sup2i.food.subscription.api.dto.IssueFoodPassRequest;
import com.sup2i.food.subscription.api.dto.MealUsageResponse;
import com.sup2i.food.subscription.service.FoodPassService;
import com.sup2i.food.subscription.service.MealUsageService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(
    name = "Admin Food Pass",
    description = "Back-office food pass management and meal consumption recording."
)
@RestController
@RequestMapping("/api/v1/admin/food-passes")
public class AdminFoodPassController {

    private final FoodPassService foodPassService;
    private final MealUsageService mealUsageService;

    public AdminFoodPassController(
        FoodPassService foodPassService,
        MealUsageService mealUsageService
    ) {
        this.foodPassService =
            foodPassService;

        this.mealUsageService =
            mealUsageService;
    }

    @GetMapping
    @PreAuthorize(
        "hasAuthority('subscription.read')"
    )
    public List<FoodPassResponse> list(
        JwtAuthenticationToken authentication
    ) {

        return foodPassService.listByOrganization(
            userId(authentication)
        );
    }

    @PostMapping
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public FoodPassResponse issue(
        @Valid
        @RequestBody
        IssueFoodPassRequest request,
        JwtAuthenticationToken authentication
    ) {

        return foodPassService.issue(
            userId(authentication),
            request.studentId()
        );
    }

    @PostMapping("/consume")
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public MealUsageResponse consume(
        @Valid
        @RequestBody
        ConsumeMealRequest request,
        JwtAuthenticationToken authentication
    ) {

        return mealUsageService.consume(
            userId(authentication),
            request.studentId(),
            request.mealType(),
            request.date()
        );
    }

    @GetMapping("/{foodPassId}/events")
    @PreAuthorize(
        "hasAuthority('subscription.read')"
    )
    public List<FoodPassEventResponse> events(
        @PathVariable UUID foodPassId,
        JwtAuthenticationToken authentication
    ) {

        return foodPassService.events(
            userId(authentication),
            foodPassId
        );
    }

    @PostMapping("/{foodPassId}/block")
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public FoodPassResponse block(
        @PathVariable UUID foodPassId,
        @RequestBody(
            required = false
        )
        FoodPassActionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return foodPassService.block(
            userId(authentication),
            foodPassId,
            request == null
                ? new FoodPassActionRequest(null)
                : request
        );
    }

    @PostMapping("/{foodPassId}/lost")
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public FoodPassResponse lost(
        @PathVariable UUID foodPassId,
        @RequestBody(
            required = false
        )
        FoodPassActionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return foodPassService.reportLost(
            userId(authentication),
            foodPassId,
            request == null
                ? new FoodPassActionRequest(null)
                : request
        );
    }

    @PostMapping("/{foodPassId}/reactivate")
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public FoodPassResponse reactivate(
        @PathVariable UUID foodPassId,
        @RequestBody(
            required = false
        )
        FoodPassActionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return foodPassService.reactivate(
            userId(authentication),
            foodPassId,
            request == null
                ? new FoodPassActionRequest(null)
                : request
        );
    }

    @PostMapping("/{foodPassId}/revoke")
    @PreAuthorize(
        "hasAuthority('subscription.write')"
    )
    public FoodPassResponse revoke(
        @PathVariable UUID foodPassId,
        @RequestBody(
            required = false
        )
        FoodPassActionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return foodPassService.revoke(
            userId(authentication),
            foodPassId,
            request == null
                ? new FoodPassActionRequest(null)
                : request
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