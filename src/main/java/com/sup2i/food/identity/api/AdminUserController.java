package com.sup2i.food.identity.api;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.identity.api.dto.AdminUserMutationResponse;
import com.sup2i.food.identity.api.dto.AdminUserResponse;
import com.sup2i.food.identity.api.dto.AssignRoleRequest;
import com.sup2i.food.identity.service.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(
    name = "Admin",
    description = "Back-office user and role management (Administration)."
)
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(
        AdminUserService service
    ) {
        this.service =
            service;
    }

    @GetMapping
    @PreAuthorize(
        "hasAuthority('user.read')"
    )
    public PageResponse<AdminUserResponse> list(
        JwtAuthenticationToken authentication,

        @RequestParam(
            defaultValue = "0"
        )
        int page,

        @RequestParam(
            defaultValue = "20"
        )
        int size
    ) {

        return service.list(
            userId(authentication),
            page,
            size
        );
    }

    @GetMapping("/{userId}")
    @PreAuthorize(
        "hasAuthority('user.read')"
    )
    public AdminUserResponse find(
        @PathVariable UUID userId,
        JwtAuthenticationToken authentication
    ) {

        return service.find(
            userId(authentication),
            userId
        );
    }

    @PostMapping("/{userId}/activate")
    @PreAuthorize(
        "hasAuthority('user.write')"
    )
    public AdminUserMutationResponse activate(
        @PathVariable UUID userId,
        JwtAuthenticationToken authentication
    ) {

        return service.activate(
            userId(authentication),
            userId
        );
    }

    @PostMapping("/{userId}/deactivate")
    @PreAuthorize(
        "hasAuthority('user.write')"
    )
    public AdminUserMutationResponse deactivate(
        @PathVariable UUID userId,
        JwtAuthenticationToken authentication
    ) {

        return service.deactivate(
            userId(authentication),
            userId
        );
    }

    @PostMapping("/{userId}/roles")
    @PreAuthorize(
        "hasAuthority('user.write')"
    )
    public AdminUserMutationResponse assignRole(
        @PathVariable UUID userId,
        @Valid
        @RequestBody
        AssignRoleRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.assignRole(
            userId(authentication),
            userId,
            request.roleCode()
        );
    }

    @PostMapping("/{userId}/roles/{roleCode}/revoke")
    @PreAuthorize(
        "hasAuthority('user.write')"
    )
    public AdminUserMutationResponse revokeRole(
        @PathVariable UUID userId,
        @PathVariable String roleCode,
        JwtAuthenticationToken authentication
    ) {

        return service.revokeRole(
            userId(authentication),
            userId,
            roleCode
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
