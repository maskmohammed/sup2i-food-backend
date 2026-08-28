package com.sup2i.food.procurement.api.dto;

import com.sup2i.food.procurement.domain.Supplier;
import com.sup2i.food.procurement.domain.SupplierStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierResponse(
    UUID id,
    String name,
    String phone,
    String email,
    String address,
    String contact,
    SupplierStatus status,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static SupplierResponse from(
        Supplier supplier
    ) {
        return new SupplierResponse(
            supplier.getId(),
            supplier.getName(),
            supplier.getPhone(),
            supplier.getEmail(),
            supplier.getAddress(),
            supplier.getContact(),
            supplier.getStatus(),
            supplier.isActive(),
            supplier.getCreatedAt(),
            supplier.getUpdatedAt()
        );
    }
}