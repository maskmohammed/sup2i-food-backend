package com.sup2i.food.menuvote.api.dto;

import com.sup2i.food.menuvote.domain.MenuVoteOption;

import java.util.UUID;

public record MenuVoteOptionResponse(
    UUID id,
    UUID productId,
    String label,
    String description,
    int displayOrder
) {

    public static MenuVoteOptionResponse from(MenuVoteOption option) {
        return new MenuVoteOptionResponse(
            option.getId(),
            option.getProduct() == null ? null : option.getProduct().getId(),
            option.getLabel(),
            option.getDescription(),
            option.getDisplayOrder()
        );
    }
}