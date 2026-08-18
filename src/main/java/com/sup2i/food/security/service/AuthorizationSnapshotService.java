package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.Permission;
import com.sup2i.food.identity.domain.UserRole;
import com.sup2i.food.identity.repository.UserRoleRepository;
import org.springframework.stereotype.Service;

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

    public AuthorizationSnapshot load(UUID userId) {

        List<UserRole> assignments =
            userRoleRepository
                .findAllWithRoleAndPermissionsByUserId(userId);

        Set<String> globalRoles = new LinkedHashSet<>();
        Set<String> globalPermissions = new LinkedHashSet<>();

        List<Map<String, Object>> scopes = assignments.stream()
            .map(assignment -> {

                boolean global =
                    assignment.getCampus() == null
                        && assignment.getLocation() == null;

                if (global) {
                    globalRoles.add(
                        assignment.getRole().getCode()
                    );

                    assignment.getRole()
                        .getPermissions()
                        .stream()
                        .map(Permission::getCode)
                        .forEach(globalPermissions::add);
                }

                return Map.<String, Object>of(
                    "role", assignment.getRole().getCode(),
                    "campusId",
                    assignment.getCampus() == null
                        ? ""
                        : assignment.getCampus()
                            .getId()
                            .toString(),
                    "locationId",
                    assignment.getLocation() == null
                        ? ""
                        : assignment.getLocation()
                            .getId()
                            .toString()
                );
            })
            .toList();

        return new AuthorizationSnapshot(
            globalRoles,
            globalPermissions,
            scopes
        );
    }

    public record AuthorizationSnapshot(
        Set<String> globalRoles,
        Set<String> globalPermissions,
        List<Map<String, Object>> roleScopes
    ) {
    }
}