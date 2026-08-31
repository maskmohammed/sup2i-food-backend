package com.sup2i.food.procurement.api.dto;

public record SupplierCommand(
    String name,
    String phone,
    String email,
    String address
) {
}