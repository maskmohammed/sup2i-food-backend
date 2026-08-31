package com.sup2i.food.voting.api.dto;

import java.util.UUID;

public record CreateMenuProposalCommand(
    UUID studentId,
    String title,
    String description
) {
}