package com.sup2i.food.security.api.dto;

import java.util.Set;
import java.util.UUID;

public record UserSummaryResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String status,
    Set<String> roles
) {
}