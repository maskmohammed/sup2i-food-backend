package com.sup2i.food.procurement.api.dto;

import com.sup2i.food.procurement.domain.SupplierContractStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSupplierContractStatusRequest(
    @NotNull
    SupplierContractStatus status
) {
}