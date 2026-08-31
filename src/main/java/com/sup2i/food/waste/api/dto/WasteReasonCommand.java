package com.sup2i.food.waste.api.dto;

import com.sup2i.food.waste.domain.WasteCategory;

public record WasteReasonCommand(
    String code,
    String name,
    WasteCategory category,
    boolean requiresComment
) {
}