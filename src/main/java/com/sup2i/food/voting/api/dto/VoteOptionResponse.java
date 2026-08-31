package com.sup2i.food.voting.api.dto;

import java.util.UUID;

public record VoteOptionResponse(
    UUID id,
    UUID campaignId,
    UUID productId,
    UUID menuProposalId,
    String label,
    String description,
    int displayOrder,
    long voteCount,
    boolean replayed
) {
}