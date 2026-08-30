package com.sup2i.food.canteen.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CanteenReservationRequest(

    @NotNull
    UUID menuId

) {
}
