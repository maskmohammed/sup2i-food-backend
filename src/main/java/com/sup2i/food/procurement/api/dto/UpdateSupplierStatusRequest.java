package com.sup2i.food.procurement.api.dto;

import com.sup2i.food.procurement.domain.SupplierStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSupplierStatusRequest(
    @NotNull
    SupplierStatus status
) {
}