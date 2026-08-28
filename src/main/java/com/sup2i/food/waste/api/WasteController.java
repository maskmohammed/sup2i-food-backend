package com.sup2i.food.waste.api;

import com.sup2i.food.waste.api.dto.CreateWasteRecordRequest;
import com.sup2i.food.waste.api.dto.WasteRecordResponse;
import com.sup2i.food.waste.api.dto.WasteStatsResponse;
import com.sup2i.food.waste.domain.WasteType;
import com.sup2i.food.waste.service.WasteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Waste", description = "Waste recording, reports, and statistics.")
@RestController
@RequestMapping(
    "/api/v1/waste"
)
public class WasteController {

    private final WasteService service;

    public WasteController(
        WasteService service
    ) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('waste.write')"
    )
    public WasteRecordResponse record(
        @Valid
        @RequestBody
        CreateWasteRecordRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.record(
            userId(authentication),
            request
        );
    }

    @GetMapping
    @PreAuthorize(
        "hasAuthority('waste.read')"
    )
    public List<WasteRecordResponse> findAll(
        @RequestParam(required = false)
        WasteType type,
        @RequestParam(
            defaultValue = "50"
        )
        int limit,
        JwtAuthenticationToken authentication
    ) {
        return service.findAll(
            userId(authentication),
            type,
            limit
        );
    }

    @GetMapping("/stats")
    @PreAuthorize(
        "hasAuthority('waste.read')"
    )
    public WasteStatsResponse stats(
        JwtAuthenticationToken authentication
    ) {
        return service.stats(
            userId(authentication)
        );
    }

    @GetMapping("/{recordId}")
    @PreAuthorize(
        "hasAuthority('waste.read')"
    )
    public WasteRecordResponse find(
        @PathVariable UUID recordId,
        JwtAuthenticationToken authentication
    ) {
        return service.find(
            userId(authentication),
            recordId
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