package com.sup2i.food.procurement.service;

import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.catalog.repository.IngredientRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.catalog.repository.ProductVariantRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.procurement.api.dto.CreateSupplierContractRequest;
import com.sup2i.food.procurement.api.dto.SupplierContractResponse;
import com.sup2i.food.procurement.api.dto.UpdateSupplierContractRequest;
import com.sup2i.food.procurement.domain.Supplier;
import com.sup2i.food.procurement.domain.SupplierContract;
import com.sup2i.food.procurement.domain.SupplierContractStatus;
import com.sup2i.food.procurement.exception.SupplierConflictException;
import com.sup2i.food.procurement.exception.SupplierNotFoundException;
import com.sup2i.food.procurement.exception.SupplierValidationException;
import com.sup2i.food.procurement.repository.SupplierContractRepository;
import com.sup2i.food.procurement.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SupplierContractService {

    private final SupplierService supplierService;
    private final SupplierRepository supplierRepository;
    private final SupplierContractRepository contractRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final IngredientRepository ingredientRepository;

    public SupplierContractService(
        SupplierService supplierService,
        SupplierRepository supplierRepository,
        SupplierContractRepository contractRepository,
        ProductRepository productRepository,
        ProductVariantRepository variantRepository,
        IngredientRepository ingredientRepository
    ) {
        this.supplierService = supplierService;
        this.supplierRepository = supplierRepository;
        this.contractRepository = contractRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @Transactional
    public SupplierContractResponse create(
        UUID actorId,
        CreateSupplierContractRequest request
    ) {
        User actor = supplierService.requiredUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        Supplier supplier =
            supplierRepository
                .findByIdAndOrganization_IdAndActiveTrue(
                    request.supplierId(),
                    organizationId
                )
                .orElseThrow(() ->
                    new SupplierNotFoundException(
                        "Supplier does not exist or is not active."
                    )
                );

        Product product =
            resolveProduct(
                request.productId(),
                organizationId
            );

        ProductVariant variant =
            resolveVariant(
                request.variantId(),
                organizationId
            );

        Ingredient ingredient =
            resolveIngredient(
                request.ingredientId(),
                organizationId
            );

        validatePeriod(
            request.startDate(),
            request.endDate()
        );

        SupplierContract contract =
            new SupplierContract(
                actor.getOrganization(),
                supplier,
                product,
                variant,
                ingredient,
                request.unitPrice(),
                request.unit(),
                request.minQuantity(),
                normalizeNullable(request.paymentTerms()),
                request.leadTimeDays(),
                request.startDate(),
                request.endDate(),
                normalizeNullable(request.notes()),
                actor
            );

        return SupplierContractResponse.from(
            contractRepository.save(contract)
        );
    }

    @Transactional(readOnly = true)
    public SupplierContractResponse find(
        UUID actorId,
        UUID contractId
    ) {
        UUID organizationId =
            supplierService.requiredUser(actorId)
                .getOrganization()
                .getId();

        return SupplierContractResponse.from(
            requiredContract(
                contractId,
                organizationId
            )
        );
    }

    @Transactional(readOnly = true)
    public List<SupplierContractResponse> findAll(
        UUID actorId,
        UUID supplierId
    ) {
        UUID organizationId =
            supplierService.requiredUser(actorId)
                .getOrganization()
                .getId();

        List<SupplierContract> contracts;

        if (supplierId != null) {

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

            contracts =
                contractRepository
                    .findAllByOrganization_IdAndSupplier_Id(
                        organizationId,
                        supplierId
                    );
        } else {
            contracts =
                contractRepository
                    .findAllByOrganization_IdOrderByCreatedAtDesc(
                        organizationId
                    );
        }

        return contracts
            .stream()
            .map(SupplierContractResponse::from)
            .toList();
    }

    @Transactional
    public SupplierContractResponse update(
        UUID actorId,
        UUID contractId,
        UpdateSupplierContractRequest request
    ) {
        User actor = supplierService.requiredUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        SupplierContract contract =
            requiredContract(
                contractId,
                organizationId
            );

        validatePeriod(
            request.startDate(),
            request.endDate()
        );

        contract.update(
            request.unitPrice(),
            request.unit(),
            request.minQuantity(),
            normalizeNullable(request.paymentTerms()),
            request.leadTimeDays(),
            request.startDate(),
            request.endDate(),
            normalizeNullable(request.notes()),
            actor
        );

        return SupplierContractResponse.from(
            contractRepository.save(contract)
        );
    }

    @Transactional
    public SupplierContractResponse setStatus(
        UUID actorId,
        UUID contractId,
        SupplierContractStatus status
    ) {
        User actor = supplierService.requiredUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        SupplierContract contract =
            requiredContract(
                contractId,
                organizationId
            );

        if (
            contract.getStatus()
                == SupplierContractStatus.EXPIRED
        ) {
            throw new SupplierConflictException(
                "An expired contract cannot be re-activated."
            );
        }

        contract.setStatus(
            status,
            actor
        );

        return SupplierContractResponse.from(
            contractRepository.save(contract)
        );
    }

    private SupplierContract requiredContract(
        UUID contractId,
        UUID organizationId
    ) {
        return contractRepository
            .findByIdAndOrganization_Id(
                contractId,
                organizationId
            )
            .orElseThrow(() ->
                new SupplierNotFoundException(
                    "Supplier contract does not exist."
                )
            );
    }

    private Product resolveProduct(
        UUID productId,
        UUID organizationId
    ) {
        if (productId == null) {
            return null;
        }

        return productRepository
            .findCatalogProduct(
                productId,
                organizationId
            )
            .orElseThrow(() ->
                new SupplierValidationException(
                    "Contract product does not exist in this organization."
                )
            );
    }

    private ProductVariant resolveVariant(
        UUID variantId,
        UUID organizationId
    ) {
        if (variantId == null) {
            return null;
        }

        return variantRepository
            .findByIdAndProduct_Organization_Id(
                variantId,
                organizationId
            )
            .orElseThrow(() ->
                new SupplierValidationException(
                    "Contract variant does not exist in this organization."
                )
            );
    }

    private Ingredient resolveIngredient(
        UUID ingredientId,
        UUID organizationId
    ) {
        if (ingredientId == null) {
            return null;
        }

        return ingredientRepository
            .findByIdAndOrganization_Id(
                ingredientId,
                organizationId
            )
            .orElseThrow(() ->
                new SupplierValidationException(
                    "Contract ingredient does not exist in this organization."
                )
            );
    }

    private void validatePeriod(
        java.time.LocalDate startDate,
        java.time.LocalDate endDate
    ) {
        if (
            startDate != null
            && endDate != null
            && !endDate.isAfter(startDate)
        ) {
            throw new SupplierValidationException(
                "Contract end date must be after the start date."
            );
        }
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