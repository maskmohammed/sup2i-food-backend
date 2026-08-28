package com.sup2i.food.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignRoleRequest(

    @NotBlank
    String roleCode
) {
}
