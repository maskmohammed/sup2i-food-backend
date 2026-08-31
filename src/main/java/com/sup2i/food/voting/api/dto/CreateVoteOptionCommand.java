package com.sup2i.food.voting.api.dto;

import java.util.UUID;

public record CreateVoteOptionCommand(
    UUID productId,
    UUID menuProposalId,
    String label,
    String description,
    int displayOrder
) {
}