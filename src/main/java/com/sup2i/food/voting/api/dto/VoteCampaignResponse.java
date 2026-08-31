package com.sup2i.food.voting.api.dto;

import com.sup2i.food.voting.domain.MenuVoteCampaignStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VoteCampaignResponse(
    UUID id,
    UUID organizationId,
    String title,
    String description,
    MenuVoteCampaignStatus status,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    int maxChoices,
    UUID createdBy,
    boolean replayed
) {
}