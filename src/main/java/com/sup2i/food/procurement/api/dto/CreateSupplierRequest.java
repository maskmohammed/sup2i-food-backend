package com.sup2i.food.procurement.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(

    @NotBlank
    @Size(max = 180)
    String name,

    @Size(max = 40)
    String phone,

    @Email
    @Size(max = 255)
    String email,

    String address,

    @Size(max = 120)
    String contact
) {
}