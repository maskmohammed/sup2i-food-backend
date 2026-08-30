package com.sup2i.food.scan.api.dto;

import java.util.UUID;

public record StudentSummary(
    UUID id,
    String studentNumber,
    String program,
    String level,
    String groupName,
    String photoUrl
) {
}