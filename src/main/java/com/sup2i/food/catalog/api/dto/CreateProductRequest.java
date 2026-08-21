package com.sup2i.food.catalog.api.dto;

import com.sup2i.food.catalog.domain.ProductType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductRequest(

    @NotNull
    UUID categoryId,

    @NotBlank
    @Size(max = 80)
    String sku,

    @NotBlank
    @Size(max = 180)
    String name,

    String description,

    String imageUrl,

    @NotNull
    ProductType productType,

    @NotNull
    @DecimalMin("0.00")
    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal basePrice,

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Digits(
        integer = 3,
        fraction = 2
    )
    BigDecimal taxRate,

    @Min(0)
    Integer preparationMinutes,

    Boolean trackStock,

    Boolean prepared,

    Boolean active
) {
}