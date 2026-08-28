package com.sup2i.food.procurement.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateSupplierContractRequest(

    @NotNull
    @DecimalMin("0.00")
    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal unitPrice,

    @NotNull
    MeasurementUnit unit,

    @DecimalMin("0.001")
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal minQuantity,

    String paymentTerms,

    Integer leadTimeDays,

    LocalDate startDate,

    LocalDate endDate,

    String notes
) {
}