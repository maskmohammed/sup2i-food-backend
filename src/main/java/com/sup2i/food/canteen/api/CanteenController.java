package com.sup2i.food.canteen.api;

import com.sup2i.food.canteen.api.dto.CanteenMenuResponse;
import com.sup2i.food.canteen.api.dto.CanteenReservationRequest;
import com.sup2i.food.canteen.api.dto.CanteenReservationResponse;
import com.sup2i.food.canteen.api.dto.MealDistributionRequest;
import com.sup2i.food.canteen.api.dto.MealUsageResponse;
import com.sup2i.food.canteen.service.CanteenMenuService;
import com.sup2i.food.canteen.service.CanteenReservationService;
import com.sup2i.food.canteen.service.MealDistributionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/canteen")
public class CanteenController {

    private final CanteenMenuService
        menuService;

    private final CanteenReservationService
        reservationService;

    private final MealDistributionService
        distributionService;

    public CanteenController(
        CanteenMenuService menuService,
        CanteenReservationService reservationService,
        MealDistributionService distributionService
    ) {
        this.menuService =
            menuService;

        this.reservationService =
            reservationService;

        this.distributionService =
            distributionService;
    }

    @GetMapping("/menus")
    @PreAuthorize(
        "hasAuthority('canteen.menu.read')"
    )
    public List<CanteenMenuResponse> menus(
        JwtAuthenticationToken authentication,

        @RequestParam
        UUID locationId,

        @RequestParam(
            required = false
        )
        @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        )
        LocalDate from,

        @RequestParam(
            required = false
        )
        @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        )
        LocalDate to
    ) {

        return menuService.list(
            actorId(authentication),
            locationId,
            from,
            to
        );
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('canteen.reserve')"
    )
    public CanteenReservationResponse reserve(
        JwtAuthenticationToken authentication,

        @NotBlank
        @Size(
            min = 8,
            max = 160
        )
        @RequestHeader(
            name = "Idempotency-Key",
            required = true
        )
        String idempotencyKey,

        @Valid
        @RequestBody
        CanteenReservationRequest request
    ) {

        return reservationService.reserve(
            actorId(authentication),
            idempotencyKey,
            request
        );
    }

    @PostMapping("/distributions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('canteen.distribute')"
    )
    public MealUsageResponse distribute(
        JwtAuthenticationToken authentication,

        @NotBlank
        @Size(
            min = 8,
            max = 160
        )
        @RequestHeader(
            name = "Idempotency-Key",
            required = true
        )
        String idempotencyKey,

        @Valid
        @RequestBody
        MealDistributionRequest request
    ) {

        return distributionService.distribute(
            actorId(authentication),
            idempotencyKey,
            request
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
