package com.sup2i.food.slot.api;

import com.sup2i.food.slot.api.dto.TimeSlotResponse;
import com.sup2i.food.slot.service.TimeSlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
    "/api/v1/slots"
)
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    public TimeSlotController(
        TimeSlotService timeSlotService
    ) {
        this.timeSlotService =
            timeSlotService;
    }

    @GetMapping
    public List<TimeSlotResponse> list(
        @RequestParam
        UUID locationId,

        @RequestParam
        @DateTimeFormat(
            iso =
                DateTimeFormat.ISO.DATE
        )
        LocalDate date,

        JwtAuthenticationToken authentication
    ) {

        return timeSlotService.list(
            actorId(
                authentication
            ),
            locationId,
            date
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

        } catch (
            NullPointerException
            | IllegalArgumentException exception
        ) {

            throw new BadCredentialsException(
                "Invalid JWT subject."
            );
        }
    }
}