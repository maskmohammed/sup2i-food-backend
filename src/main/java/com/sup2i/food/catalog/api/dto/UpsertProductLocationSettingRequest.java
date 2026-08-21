package com.sup2i.food.catalog.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

public record UpsertProductLocationSettingRequest(

    Boolean enabled,

    @Size(max = 7)
    List<
        @Min(1)
        @Max(7)
        Integer
    > allowedDays,

    LocalTime availableFromTime,

    LocalTime availableToTime,

    @Min(0)
    Integer preparationMinutes
) {

    @AssertTrue(
        message =
            "availableToTime must be after availableFromTime"
    )
    public boolean isTimeRangeValid() {

        if (
            availableFromTime == null
            || availableToTime == null
        ) {
            return true;
        }

        return availableToTime
            .isAfter(availableFromTime);
    }

    @AssertTrue(
        message =
            "allowedDays must not contain duplicates"
    )
    public boolean isAllowedDaysUnique() {

        if (allowedDays == null) {
            return true;
        }

        return new HashSet<>(
            allowedDays
        ).size() == allowedDays.size();
    }
}