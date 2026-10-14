package com.sup2i.food.security.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("scopedAuthorization")
public class ScopedAuthorizationService {

    private final JdbcTemplate jdbcTemplate;

    public ScopedAuthorizationService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasCampusPermission(
        Authentication authentication,
        String permissionCode,
        UUID campusId
    ) {
        UUID actorId = actorId(authentication);

        if (
            actorId == null
            || permissionCode == null
            || permissionCode.isBlank()
            || campusId == null
        ) {
            return false;
        }

        if (hasGlobalPermission(actorId, permissionCode)) {
            return true;
        }

        return exists(
            """
            SELECT EXISTS (
                SELECT 1
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                JOIN user_roles ur
                  ON ur.user_id = u.id
                JOIN role_permissions rp
                  ON rp.role_id = ur.role_id
                JOIN permissions p
                  ON p.id = rp.permission_id
                JOIN campuses c
                  ON c.id = ?
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                  AND c.is_active = TRUE
                  AND c.organization_id = u.organization_id
                  AND p.code = ?
                  AND ur.campus_id = c.id
                  AND ur.location_id IS NULL
            )
            """,
            campusId,
            actorId,
            permissionCode
        );
    }

    public boolean hasLocationPermission(
        Authentication authentication,
        String permissionCode,
        UUID locationId
    ) {
        UUID actorId = actorId(authentication);

        if (
            actorId == null
            || permissionCode == null
            || permissionCode.isBlank()
            || locationId == null
        ) {
            return false;
        }

        if (hasGlobalPermission(actorId, permissionCode)) {
            return true;
        }

        return exists(
            """
            SELECT EXISTS (
                SELECT 1
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                JOIN user_roles ur
                  ON ur.user_id = u.id
                JOIN role_permissions rp
                  ON rp.role_id = ur.role_id
                JOIN permissions p
                  ON p.id = rp.permission_id
                JOIN locations l
                  ON l.id = ?
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                  AND c.is_active = TRUE
                  AND l.is_active = TRUE
                  AND c.organization_id = u.organization_id
                  AND p.code = ?
                  AND ur.campus_id = c.id
                  AND (
                        ur.location_id IS NULL
                        OR ur.location_id = l.id
                  )
            )
            """,
            locationId,
            actorId,
            permissionCode
        );
    }

    public boolean hasMenuPermission(
        Authentication authentication,
        String permissionCode,
        UUID menuId
    ) {
        UUID actorId = actorId(authentication);

        if (
            actorId == null
            || permissionCode == null
            || permissionCode.isBlank()
            || menuId == null
        ) {
            return false;
        }

        if (hasGlobalPermission(actorId, permissionCode)) {
            return true;
        }

        return exists(
            """
            SELECT EXISTS (
                SELECT 1
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                JOIN user_roles ur
                  ON ur.user_id = u.id
                JOIN role_permissions rp
                  ON rp.role_id = ur.role_id
                JOIN permissions p
                  ON p.id = rp.permission_id
                JOIN canteen_menus cm
                  ON cm.id = ?
                JOIN locations l
                  ON l.id = cm.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                  AND c.is_active = TRUE
                  AND l.is_active = TRUE
                  AND c.organization_id = u.organization_id
                  AND p.code = ?
                  AND ur.campus_id = c.id
                  AND (
                        ur.location_id IS NULL
                        OR ur.location_id = l.id
                  )
            )
            """,
            menuId,
            actorId,
            permissionCode
        );
    }

    public boolean hasTerminalPermission(
        Authentication authentication,
        String permissionCode,
        UUID terminalId
    ) {
        UUID actorId = actorId(authentication);

        if (
            actorId == null
            || permissionCode == null
            || permissionCode.isBlank()
            || terminalId == null
        ) {
            return false;
        }

        if (hasGlobalPermission(actorId, permissionCode)) {
            return true;
        }

        return exists(
            """
            SELECT EXISTS (
                SELECT 1
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                JOIN user_roles ur
                  ON ur.user_id = u.id
                JOIN role_permissions rp
                  ON rp.role_id = ur.role_id
                JOIN permissions p
                  ON p.id = rp.permission_id
                JOIN pos_terminals pt
                  ON pt.id = ?
                JOIN locations l
                  ON l.id = pt.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                  AND pt.is_active = TRUE
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                  AND c.organization_id = u.organization_id
                  AND p.code = ?
                  AND ur.campus_id = c.id
                  AND (
                        ur.location_id IS NULL
                        OR ur.location_id = l.id
                  )
            )
            """,
            terminalId,
            actorId,
            permissionCode
        );
    }

    public boolean hasDistributionPermission(
        Authentication authentication,
        String permissionCode,
        UUID terminalId,
        UUID menuId
    ) {
        UUID actorId = actorId(authentication);

        if (
            actorId == null
            || permissionCode == null
            || permissionCode.isBlank()
        ) {
            return false;
        }

        if (hasGlobalPermission(actorId, permissionCode)) {
            return true;
        }

        if (terminalId != null) {
            return hasTerminalPermission(
                authentication,
                permissionCode,
                terminalId
            );
        }

        if (menuId != null) {
            return hasMenuPermission(
                authentication,
                permissionCode,
                menuId
            );
        }

        return false;
    }

    private boolean hasGlobalPermission(
        UUID actorId,
        String permissionCode
    ) {
        return exists(
            """
            SELECT EXISTS (
                SELECT 1
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                JOIN user_roles ur
                  ON ur.user_id = u.id
                JOIN role_permissions rp
                  ON rp.role_id = ur.role_id
                JOIN permissions p
                  ON p.id = rp.permission_id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                  AND p.code = ?
                  AND ur.campus_id IS NULL
                  AND ur.location_id IS NULL
            )
            """,
            actorId,
            permissionCode
        );
    }

    private boolean exists(
        String sql,
        Object... arguments
    ) {
        Boolean result =
            jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                arguments
            );

        return Boolean.TRUE.equals(result);
    }

    private UUID actorId(
        Authentication authentication
    ) {
        if (
            authentication == null
            || !authentication.isAuthenticated()
            || !(authentication instanceof JwtAuthenticationToken jwt)
        ) {
            return null;
        }

        String subject =
            jwt.getToken()
                .getSubject();

        if (
            subject == null
            || subject.isBlank()
        ) {
            return null;
        }

        try {
            return UUID.fromString(subject);
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
    }
}