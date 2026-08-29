package com.sup2i.food.pos.api;

import com.sup2i.food.pos.api.dto.ClosePosSessionRequest;
import com.sup2i.food.pos.api.dto.ForceClosePosSessionRequest;
import com.sup2i.food.pos.api.dto.OpenPosSessionRequest;
import com.sup2i.food.pos.api.dto.PosSessionResponse;
import com.sup2i.food.pos.service.PosReconciliationService;
import com.sup2i.food.pos.service.PosSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping(
    "/api/v1/pos/sessions"
)
public class PosSessionController {

    private final PosSessionService sessionService;

    private final PosReconciliationService
        reconciliationService;

    public PosSessionController(
        PosSessionService sessionService,
        PosReconciliationService reconciliationService
    ) {
        this.sessionService =
            sessionService;

        this.reconciliationService =
            reconciliationService;
    }

    @PostMapping
    @ResponseStatus(
        HttpStatus.CREATED
    )
    @PreAuthorize(
        "hasAuthority('pos.open')"
    )
    public PosSessionResponse open(
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
        OpenPosSessionRequest request,

        JwtAuthenticationToken authentication
    ) {

        return sessionService.open(
            actorId(
                authentication
            ),
            request.terminalId(),
            request.openingCash(),
            idempotencyKey
        );
    }

    @PostMapping(
        "/{sessionId}/close"
    )
    @PreAuthorize(
        "hasAuthority('pos.close')"
    )
    public PosSessionResponse close(
        @PathVariable
        UUID sessionId,

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
        ClosePosSessionRequest request,

        JwtAuthenticationToken authentication
    ) {

        return reconciliationService.close(
            actorId(
                authentication
            ),
            sessionId,
            request.countedCash(),
            request.comment(),
            idempotencyKey
        );
    }

    @PostMapping(
        "/{sessionId}/force-close"
    )
    @PreAuthorize(
        "hasAuthority('pos.force_close')"
    )
    public PosSessionResponse forceClose(
        @PathVariable
        UUID sessionId,

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
        ForceClosePosSessionRequest request,

        JwtAuthenticationToken authentication
    ) {

        return reconciliationService.forceClose(
            actorId(
                authentication
            ),
            sessionId,
            request.countedCash(),
            request.comment(),
            idempotencyKey
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