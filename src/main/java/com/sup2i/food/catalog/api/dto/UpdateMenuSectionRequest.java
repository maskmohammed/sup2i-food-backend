package com.sup2i.food.catalog.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMenuSectionRequest(

    @Size(max = 80)
    String code,

    @NotBlank
    @Size(max = 120)
    String name,

    @Min(0)
    Integer minSelect,

    @Min(0)
    Integer maxSelect,

    @Min(0)
    int displayOrder,

    Boolean active
) {

    @AssertTrue(
        message =
            "minSelect must be less than or equal to maxSelect"
    )
    public boolean isSelectionRangeValid() {

        if (
            minSelect == null
            || maxSelect == null
        ) {
            return true;
        }

        return minSelect <= maxSelect;
    }
}