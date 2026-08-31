package com.sup2i.food.voting.api.dto;

import java.time.OffsetDateTime;

public record CreateVoteCampaignCommand(
    String title,
    String description,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    int maxChoices
) {
}