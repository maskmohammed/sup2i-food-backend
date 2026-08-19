package com.sup2i.food.security.api.dto;

import java.util.UUID;

public record StudentSummaryResponse(
    UUID id,
    String studentNumber,
    String program,
    String level,
    String groupName,
    String photoUrl
) {
}