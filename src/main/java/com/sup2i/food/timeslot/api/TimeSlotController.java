package com.sup2i.food.timeslot.api;

import com.sup2i.food.timeslot.api.dto.TimeSlotResponse;
import com.sup2i.food.timeslot.service.TimeSlotService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(
    name = "Time Slots",
    description = "Pickup time slot availability."
)
@RestController
@RequestMapping("/api/v1/time-slots")
@PreAuthorize("isAuthenticated()")
public class TimeSlotController {

    private final TimeSlotService service;

    public TimeSlotController(
        TimeSlotService service
    ) {
        this.service =
            service;
    }

    @GetMapping
    public List<TimeSlotResponse> list(
        @RequestParam
        UUID locationId,

        @RequestParam(
            required = false
        )
        @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        )
        LocalDate date
    ) {

        return service.listAvailable(
            locationId,
            date == null
                ? LocalDate.now()
                : date
        );
    }
}
