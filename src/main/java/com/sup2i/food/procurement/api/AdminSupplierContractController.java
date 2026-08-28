package com.sup2i.food.procurement.api;

import com.sup2i.food.procurement.api.dto.CreateSupplierContractRequest;
import com.sup2i.food.procurement.api.dto.SupplierContractResponse;
import com.sup2i.food.procurement.api.dto.UpdateSupplierContractRequest;
import com.sup2i.food.procurement.api.dto.UpdateSupplierContractStatusRequest;
import com.sup2i.food.procurement.service.SupplierContractService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Supplier contracts", description = "Supplier pricing contracts administration.")
@RestController
@RequestMapping(
    "/api/v1/admin/supplier-contracts"
)
public class AdminSupplierContractController {

    private final SupplierContractService service;

    public AdminSupplierContractController(
        SupplierContractService service
    ) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('supplier.write')"
    )
    public SupplierContractResponse create(
        @Valid
        @RequestBody
        CreateSupplierContractRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.create(
            userId(authentication),
            request
        );
    }

    @GetMapping
    @PreAuthorize(
        "hasAuthority('supplier.read')"
    )
    public List<SupplierContractResponse> findAll(
        @RequestParam(required = false)
        UUID supplierId,
        JwtAuthenticationToken authentication
    ) {
        return service.findAll(
            userId(authentication),
            supplierId
        );
    }

    @GetMapping("/{contractId}")
    @PreAuthorize(
        "hasAuthority('supplier.read')"
    )
    public SupplierContractResponse find(
        @PathVariable UUID contractId,
        JwtAuthenticationToken authentication
    ) {
        return service.find(
            userId(authentication),
            contractId
        );
    }

    @PutMapping("/{contractId}")
    @PreAuthorize(
        "hasAuthority('supplier.write')"
    )
    public SupplierContractResponse update(
        @PathVariable UUID contractId,
        @Valid
        @RequestBody
        UpdateSupplierContractRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.update(
            userId(authentication),
            contractId,
            request
        );
    }

    @PatchMapping("/{contractId}/status")
    @PreAuthorize(
        "hasAuthority('supplier.write')"
    )
    public SupplierContractResponse setStatus(
        @PathVariable UUID contractId,
        @Valid
        @RequestBody
        UpdateSupplierContractStatusRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.setStatus(
            userId(authentication),
            contractId,
            request.status()
        );
    }

    private UUID userId(
        JwtAuthenticationToken authentication
    ) {
        try {
            return UUID.fromString(
                authentication
                    .getToken()
                    .getSubject()
            );
        } catch (
            IllegalArgumentException exception
        ) {
            throw new BadCredentialsException(
                "Invalid JWT subject."
            );
        }
    }
}