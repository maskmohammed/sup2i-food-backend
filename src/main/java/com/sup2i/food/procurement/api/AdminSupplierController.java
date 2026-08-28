package com.sup2i.food.procurement.api;

import com.sup2i.food.procurement.api.dto.CreateSupplierRequest;
import com.sup2i.food.procurement.api.dto.SupplierResponse;
import com.sup2i.food.procurement.api.dto.UpdateSupplierRequest;
import com.sup2i.food.procurement.api.dto.UpdateSupplierStatusRequest;
import com.sup2i.food.procurement.domain.SupplierStatus;
import com.sup2i.food.procurement.service.SupplierService;
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

@Tag(name = "Suppliers", description = "Supplier directory administration.")
@RestController
@RequestMapping(
    "/api/v1/admin/suppliers"
)
public class AdminSupplierController {

    private final SupplierService service;

    public AdminSupplierController(
        SupplierService service
    ) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('supplier.write')"
    )
    public SupplierResponse create(
        @Valid
        @RequestBody
        CreateSupplierRequest request,
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
    public List<SupplierResponse> findAll(
        @RequestParam(required = false)
        Boolean active,
        JwtAuthenticationToken authentication
    ) {
        return service.findAll(
            userId(authentication),
            active
        );
    }

    @GetMapping("/{supplierId}")
    @PreAuthorize(
        "hasAuthority('supplier.read')"
    )
    public SupplierResponse find(
        @PathVariable UUID supplierId,
        JwtAuthenticationToken authentication
    ) {
        return service.find(
            userId(authentication),
            supplierId
        );
    }

    @PutMapping("/{supplierId}")
    @PreAuthorize(
        "hasAuthority('supplier.write')"
    )
    public SupplierResponse update(
        @PathVariable UUID supplierId,
        @Valid
        @RequestBody
        UpdateSupplierRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.update(
            userId(authentication),
            supplierId,
            request
        );
    }

    @PatchMapping("/{supplierId}/status")
    @PreAuthorize(
        "hasAuthority('supplier.write')"
    )
    public SupplierResponse setStatus(
        @PathVariable UUID supplierId,
        @Valid
        @RequestBody
        UpdateSupplierStatusRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.setStatus(
            userId(authentication),
            supplierId,
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