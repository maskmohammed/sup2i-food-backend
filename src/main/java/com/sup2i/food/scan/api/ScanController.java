package com.sup2i.food.scan.api;

import com.sup2i.food.scan.api.dto.ScanRequest;
import com.sup2i.food.scan.api.dto.ScanResponse;
import com.sup2i.food.scan.service.ScanService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping(
    "/api/v1/scan"
)
public class ScanController {

    private final ScanService service;

    public ScanController(
        ScanService service
    ) {
        this.service =
            service;
    }

    @PostMapping(
        "/resolve"
    )
    public ScanResponse resolve(
        @Valid
        @RequestBody
        ScanRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.resolve(
            userId(
                authentication
            ),
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
            NullPointerException
            | IllegalArgumentException exception
        ) {

            throw new BadCredentialsException(
                "Invalid JWT subject."
            );
        }
    }
}