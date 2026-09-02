package com.sup2i.food.canteen.api.dto;

import com.sup2i.food.scan.api.dto.FoodPassResponse;

public record FoodPassReplacementResponse(
    FoodPassResponse foodPass,
    String credential
) {
}