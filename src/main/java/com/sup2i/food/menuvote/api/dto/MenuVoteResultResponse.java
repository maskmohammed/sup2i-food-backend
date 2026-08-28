package com.sup2i.food.menuvote.api.dto;

import com.sup2i.food.menuvote.domain.MenuVoteStatus;

import java.util.List;
import java.util.UUID;

public record MenuVoteResultResponse(
    UUID sessionId,
    String title,
    MenuVoteStatus status,
    int totalVotes,
    List<OptionResult> options
) {

    public record OptionResult(
        UUID id,
        UUID productId,
        String label,
        long votes
    ) {
    }
}