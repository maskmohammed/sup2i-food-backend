package com.sup2i.food.catalog.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductBarcodeRequest(

    UUID variantId,

    @NotBlank
    @Size(max = 120)
    String barcode,

    @DecimalMin("0.001")
    BigDecimal packQuantity,

    Boolean primary,

    Boolean active
) {
}