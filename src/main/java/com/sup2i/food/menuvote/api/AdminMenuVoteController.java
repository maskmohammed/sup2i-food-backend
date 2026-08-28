package com.sup2i.food.menuvote.api;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.menuvote.api.dto.CreateMenuVoteSessionRequest;
import com.sup2i.food.menuvote.api.dto.MenuVoteResultResponse;
import com.sup2i.food.menuvote.api.dto.MenuVoteSessionResponse;
import com.sup2i.food.menuvote.domain.MenuVoteStatus;
import com.sup2i.food.menuvote.service.MenuVoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin Menu Votes", description = "Back-office menu-vote sessions.")
@RestController
@RequestMapping("/api/v1/admin/menu-votes")
public class AdminMenuVoteController {

    private final MenuVoteService service;

    public AdminMenuVoteController(
        MenuVoteService service
    ) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('menuvote.write')")
    public MenuVoteSessionResponse create(
        @Valid
        @RequestBody
        CreateMenuVoteSessionRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.create(
            userId(authentication),
            request
        );
    }

    @GetMapping
    @PreAuthorize("hasAuthority('menuvote.read')")
    public PageResponse<MenuVoteSessionResponse> list(
        JwtAuthenticationToken authentication,
        @RequestParam(required = false)
        MenuVoteStatus status,
        @RequestParam(defaultValue = "0")
        int page,
        @RequestParam(defaultValue = "20")
        int size
    ) {
        return service.list(
            userId(authentication),
            status,
            page,
            size
        );
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAuthority('menuvote.read')")
    public MenuVoteSessionResponse get(
        @PathVariable UUID sessionId,
        JwtAuthenticationToken authentication
    ) {
        return service.get(
            userId(authentication),
            sessionId
        );
    }

    @PatchMapping("/{sessionId}/close")
    @PreAuthorize("hasAuthority('menuvote.write')")
    public MenuVoteSessionResponse close(
        @PathVariable UUID sessionId,
        JwtAuthenticationToken authentication
    ) {
        return service.close(
            userId(authentication),
            sessionId
        );
    }

    @GetMapping("/{sessionId}/results")
    @PreAuthorize("hasAuthority('menuvote.read')")
    public MenuVoteResultResponse results(
        @PathVariable UUID sessionId,
        JwtAuthenticationToken authentication
    ) {
        return service.results(
            userId(authentication),
            sessionId
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