package com.sup2i.food.procurement.repository;

import com.sup2i.food.procurement.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository
    extends JpaRepository<Supplier, UUID> {

    Optional<Supplier>
        findByIdAndOrganization_IdAndActiveTrue(
            UUID id,
            UUID organizationId
        );
}