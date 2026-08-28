package com.sup2i.food.identity.api.dto;

import java.util.Set;
import java.util.UUID;

public record AdminUserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String status,
    Set<String> roles
) {
}
