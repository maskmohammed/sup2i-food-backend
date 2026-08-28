package com.sup2i.food.menuvote.api.dto;

import com.sup2i.food.menuvote.domain.MenuVoteSession;
import com.sup2i.food.menuvote.domain.MenuVoteStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MenuVoteSessionResponse(
    UUID id,
    String title,
    String description,
    LocalDate targetWeek,
    OffsetDateTime voteDeadline,
    MenuVoteStatus status,
    OffsetDateTime createdAt,
    List<MenuVoteOptionResponse> options
) {

    public static MenuVoteSessionResponse from(MenuVoteSession session) {
        return new MenuVoteSessionResponse(
            session.getId(),
            session.getTitle(),
            session.getDescription(),
            session.getTargetWeek(),
            session.getVoteDeadline(),
            session.getStatus(),
            session.getCreatedAt(),
            session.getOptions()
                .stream()
                .map(MenuVoteOptionResponse::from)
                .toList()
        );
    }
}