package com.sup2i.food.identity.api.dto;

public record AdminUserMutationResponse(
    AdminUserResponse user,
    boolean replayed
) {
}
