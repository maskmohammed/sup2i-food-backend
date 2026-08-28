package com.sup2i.food.procurement.repository;

import com.sup2i.food.procurement.domain.SupplierContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierContractRepository
    extends JpaRepository<
        SupplierContract,
        UUID
    > {

    Optional<SupplierContract>
        findByIdAndOrganization_Id(
            UUID id,
            UUID organizationId
        );

    List<SupplierContract>
        findAllByOrganization_IdOrderByCreatedAtDesc(
            UUID organizationId
        );

    List<SupplierContract>
        findAllByOrganization_IdAndSupplier_Id(
            UUID organizationId,
            UUID supplierId
        );
}