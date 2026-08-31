package com.sup2i.food.voting.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VoteResponse(
    UUID id,
    UUID campaignId,
    UUID optionId,
    UUID studentId,
    OffsetDateTime createdAt,
    boolean replayed
) {
}