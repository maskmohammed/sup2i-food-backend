package com.sup2i.food.procurement.api.dto;

import com.sup2i.food.procurement.domain.SupplierContract;
import com.sup2i.food.procurement.domain.SupplierContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierContractResponse(
    UUID id,
    UUID supplierId,
    String supplierName,
    UUID productId,
    UUID variantId,
    UUID ingredientId,
    BigDecimal unitPrice,
    String unit,
    BigDecimal minQuantity,
    String paymentTerms,
    Integer leadTimeDays,
    LocalDate startDate,
    LocalDate endDate,
    SupplierContractStatus status,
    String notes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static SupplierContractResponse from(
        SupplierContract contract
    ) {
        return new SupplierContractResponse(
            contract.getId(),
            contract.getSupplier()
                .getId(),
            contract.getSupplier()
                .getName(),
            contract.getProduct() == null
                ? null
                : contract.getProduct()
                    .getId(),
            contract.getVariant() == null
                ? null
                : contract.getVariant()
                    .getId(),
            contract.getIngredient() == null
                ? null
                : contract.getIngredient()
                    .getId(),
            contract.getUnitPrice(),
            contract.getUnit()
                .name(),
            contract.getMinQuantity(),
            contract.getPaymentTerms(),
            contract.getLeadTimeDays(),
            contract.getStartDate(),
            contract.getEndDate(),
            contract.getStatus(),
            contract.getNotes(),
            contract.getCreatedAt(),
            contract.getUpdatedAt()
        );
    }
}