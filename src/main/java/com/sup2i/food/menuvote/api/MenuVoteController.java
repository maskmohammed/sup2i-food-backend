package com.sup2i.food.menuvote.api;

import com.sup2i.food.menuvote.api.dto.MenuVoteResultResponse;
import com.sup2i.food.menuvote.api.dto.MenuVoteSessionResponse;
import com.sup2i.food.menuvote.api.dto.VoteRequest;
import com.sup2i.food.menuvote.service.MenuVoteService;
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

import java.util.UUID;

@Tag(name = "Menu Votes", description = "Student menu voting: current session, cast a vote, results.")
@RestController
@RequestMapping("/api/v1/menu-votes")
public class MenuVoteController {

    private final MenuVoteService service;

    public MenuVoteController(
        MenuVoteService service
    ) {
        this.service = service;
    }

    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public MenuVoteSessionResponse current(
        JwtAuthenticationToken authentication
    ) {
        return service.current(
            userId(authentication)
        );
    }

    @PostMapping("/{sessionId}/vote")
    @PreAuthorize("isAuthenticated()")
    public MenuVoteSessionResponse vote(
        @PathVariable UUID sessionId,
        @Valid
        @RequestBody
        VoteRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.vote(
            userId(authentication),
            sessionId,
            request
        );
    }

    @GetMapping("/{sessionId}/results")
    @PreAuthorize("isAuthenticated()")
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