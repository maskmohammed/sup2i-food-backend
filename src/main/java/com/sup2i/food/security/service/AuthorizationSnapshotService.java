package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.Permission;
import com.sup2i.food.identity.domain.UserRole;
import com.sup2i.food.identity.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthorizationSnapshotService {

    private final UserRoleRepository userRoleRepository;

    public AuthorizationSnapshotService(
        UserRoleRepository userRoleRepository
    ) {
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public AuthorizationSnapshot load(UUID userId) {

        List<UserRole> assignments =
            userRoleRepository
                .findAllWithRoleAndPermissionsByUserId(userId);

        Set<String> allRoles = new LinkedHashSet<>();
        Set<String> globalRoles = new LinkedHashSet<>();
        Set<String> globalPermissions = new LinkedHashSet<>();

        List<Map<String, Object>> scopes = assignments.stream()
            .map(assignment -> {

                String roleCode =
                    assignment.getRole().getCode();

                allRoles.add(roleCode);

                boolean global =
                    assignment.getCampus() == null
                        && assignment.getLocation() == null;

                if (global) {
                    globalRoles.add(roleCode);

                    assignment.getRole()
                        .getPermissions()
                        .stream()
                        .map(Permission::getCode)
                        .forEach(globalPermissions::add);
                }

                Map<String, Object> scope =
                    new LinkedHashMap<>();

                scope.put("role", roleCode);

                if (assignment.getCampus() != null) {
                    scope.put(
                        "campusId",
                        assignment.getCampus()
                            .getId()
                            .toString()
                    );
                }

                if (assignment.getLocation() != null) {
                    scope.put(
                        "locationId",
                        assignment.getLocation()
                            .getId()
                            .toString()
                    );
                }

                return scope;
            })
            .toList();

        return new AuthorizationSnapshot(
            allRoles,
            globalRoles,
            globalPermissions,
            scopes
        );
    }

    public record AuthorizationSnapshot(
        Set<String> allRoles,
        Set<String> globalRoles,
        Set<String> globalPermissions,
        List<Map<String, Object>> roleScopes
    ) {
    }
}