package com.sup2i.food.procurement.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.procurement.api.dto.CreateSupplierRequest;
import com.sup2i.food.procurement.api.dto.SupplierResponse;
import com.sup2i.food.procurement.api.dto.UpdateSupplierRequest;
import com.sup2i.food.procurement.domain.Supplier;
import com.sup2i.food.procurement.domain.SupplierStatus;
import com.sup2i.food.procurement.exception.SupplierNotFoundException;
import com.sup2i.food.procurement.repository.SupplierRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SupplierService {

    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;

    public SupplierService(
        UserRepository userRepository,
        SupplierRepository supplierRepository
    ) {
        this.userRepository = userRepository;
        this.supplierRepository = supplierRepository;
    }

    @Transactional
    public SupplierResponse create(
        UUID actorId,
        CreateSupplierRequest request
    ) {
        User actor = requiredUser(actorId);

        Supplier supplier =
            new Supplier(
                actor.getOrganization(),
                normalizeRequired(request.name()),
                normalizeNullable(request.phone()),
                normalizeNullable(request.email()),
                normalizeNullable(request.address()),
                normalizeNullable(request.contact()),
                SupplierStatus.ACTIVE
            );

        return SupplierResponse.from(
            supplierRepository.save(supplier)
        );
    }

    @Transactional(readOnly = true)
    public SupplierResponse find(
        UUID actorId,
        UUID supplierId
    ) {
        UUID organizationId =
            requiredUser(actorId)
                .getOrganization()
                .getId();

        Supplier supplier =
            supplierRepository
                .findByIdAndOrganization_Id(
                    supplierId,
                    organizationId
                )
                .orElseThrow(() ->
                    new SupplierNotFoundException(
                        "Supplier does not exist."
                    )
                );

        return SupplierResponse.from(supplier);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> findAll(
        UUID actorId,
        Boolean active
    ) {
        UUID organizationId =
            requiredUser(actorId)
                .getOrganization()
                .getId();

        List<Supplier> suppliers;

        if (
            active != null
            && active
        ) {
            suppliers =
                supplierRepository
                    .findAllByOrganization_IdAndActiveTrueOrderByNameAsc(
                        organizationId
                    );
        } else {
            suppliers =
                supplierRepository
                    .findAllByOrganization_IdOrderByNameAsc(
                        organizationId
                    );
        }

        return suppliers
            .stream()
            .map(SupplierResponse::from)
            .toList();
    }

    @Transactional
    public SupplierResponse update(
        UUID actorId,
        UUID supplierId,
        UpdateSupplierRequest request
    ) {
        UUID organizationId =
            requiredUser(actorId)
                .getOrganization()
                .getId();

        Supplier supplier =
            supplierRepository
                .findByIdAndOrganization_Id(
                    supplierId,
                    organizationId
                )
                .orElseThrow(() ->
                    new SupplierNotFoundException(
                        "Supplier does not exist."
                    )
                );

        supplier.update(
            normalizeRequired(request.name()),
            normalizeNullable(request.phone()),
            normalizeNullable(request.email()),
            normalizeNullable(request.address()),
            normalizeNullable(request.contact())
        );

        return SupplierResponse.from(
            supplierRepository.save(supplier)
        );
    }

    @Transactional
    public SupplierResponse setStatus(
        UUID actorId,
        UUID supplierId,
        SupplierStatus status
    ) {
        UUID organizationId =
            requiredUser(actorId)
                .getOrganization()
                .getId();

        Supplier supplier =
            supplierRepository
                .findByIdAndOrganization_Id(
                    supplierId,
                    organizationId
                )
                .orElseThrow(() ->
                    new SupplierNotFoundException(
                        "Supplier does not exist."
                    )
                );

        supplier.setStatus(status);

        return SupplierResponse.from(
            supplierRepository.save(supplier)
        );
    }

    User requiredUser(
        UUID userId
    ) {
        return userRepository
            .findById(userId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private String normalizeRequired(
        String value
    ) {
        return value == null
            ? null
            : value.trim();
    }

    private String normalizeNullable(
        String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }
}