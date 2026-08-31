package com.sup2i.food.voting.api.dto;

import com.sup2i.food.voting.domain.MenuProposalStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MenuProposalResponse(
    UUID id,
    UUID organizationId,
    UUID studentId,
    String title,
    String description,
    MenuProposalStatus status,
    UUID reviewedBy,
    OffsetDateTime reviewedAt,
    OffsetDateTime createdAt,
    boolean replayed
) {
}