package com.sup2i.food.security;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.service.AuthenticationTokens;
import com.sup2i.food.security.service.RefreshTokenService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetAddress;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ScopedAuthorizationE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_test"
            );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    void productionMenuEndpointEnforcesGlobalCampusLocationAndTenantScopes()
        throws Exception {

        UUID organizationId =
            organization(
                "SCOPED"
            );

        UUID campusA =
            campus(
                organizationId,
                "A"
            );

        UUID campusB =
            campus(
                organizationId,
                "B"
            );

        UUID locationA =
            location(
                campusA,
                "A1"
            );

        UUID locationA2 =
            location(
                campusA,
                "A2"
            );

        UUID locationB =
            location(
                campusB,
                "B1"
            );

        Actor global =
            actor(
                organizationId,
                null,
                null,
                "GLOBAL"
            );

        Actor campusScoped =
            actor(
                organizationId,
                campusA,
                null,
                "CAMPUS"
            );

        Actor locationScoped =
            actor(
                organizationId,
                campusA,
                locationA,
                "LOCATION"
            );

        menus(
            campusScoped,
            locationA
        )
            .andExpect(
                status().isOk()
            );

        menus(
            campusScoped,
            locationA2
        )
            .andExpect(
                status().isOk()
            );

        menus(
            campusScoped,
            locationB
        )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PERMISSION_DENIED")
            );

        menus(
            locationScoped,
            locationA
        )
            .andExpect(
                status().isOk()
            );

        menus(
            locationScoped,
            locationA2
        )
            .andExpect(
                status().isForbidden()
            );

        menus(
            global,
            locationB
        )
            .andExpect(
                status().isOk()
            );

        UUID foreignOrganization =
            organization(
                "FOREIGN"
            );

        UUID foreignCampus =
            campus(
                foreignOrganization,
                "FOREIGN"
            );

        UUID foreignLocation =
            location(
                foreignCampus,
                "FOREIGN"
            );

        menus(
            global,
            foreignLocation
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$")
                    .isEmpty()
            );

        menus(
            campusScoped,
            foreignLocation
        )
            .andExpect(
                status().isForbidden()
            );
    }

    private ResultActions menus(
        Actor actor,
        UUID locationId
    ) throws Exception {

        return mockMvc.perform(
            get(
                "/api/v1/canteen/menus"
            )
                .param(
                    "locationId",
                    locationId.toString()
                )
                .header(
                    "Authorization",
                    "Bearer "
                        + actor.accessToken()
                )
        );
    }

    private UUID organization(
        String prefix
    ) {
        UUID id =
            UUID.randomUUID();

        String suffix =
            UUID.randomUUID()
                .toString()
                .substring(0, 8);

        jdbcTemplate.update(
            """
            INSERT INTO organizations (
                id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            id,
            prefix + " Organization",
            prefix + "-" + suffix
        );

        return id;
    }

    private UUID campus(
        UUID organizationId,
        String prefix
    ) {
        UUID id =
            UUID.randomUUID();

        String suffix =
            UUID.randomUUID()
                .toString()
                .substring(0, 8);

        jdbcTemplate.update(
            """
            INSERT INTO campuses (
                id,
                organization_id,
                name,
                code,
                timezone,
                is_active
            )
            VALUES (
                ?, ?, ?, ?,
                'Africa/Casablanca',
                TRUE
            )
            """,
            id,
            organizationId,
            prefix + " Campus",
            prefix + "-" + suffix
        );

        return id;
    }

    private UUID location(
        UUID campusId,
        String prefix
    ) {
        UUID id =
            UUID.randomUUID();

        String suffix =
            UUID.randomUUID()
                .toString()
                .substring(0, 8);

        jdbcTemplate.update(
            """
            INSERT INTO locations (
                id,
                campus_id,
                name,
                code,
                type,
                is_active
            )
            VALUES (
                ?, ?, ?, ?,
                'SNACK',
                TRUE
            )
            """,
            id,
            campusId,
            prefix + " Location",
            prefix + "-" + suffix
        );

        return id;
    }

    private Actor actor(
        UUID organizationId,
        UUID scopeCampusId,
        UUID scopeLocationId,
        String prefix
    ) {
        UUID userId =
            UUID.randomUUID();

        String suffix =
            UUID.randomUUID()
                .toString()
                .substring(0, 8);

        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                organization_id,
                email,
                first_name,
                last_name,
                status
            )
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """,
            userId,
            organizationId,
            "scoped-"
                + prefix.toLowerCase()
                + "-"
                + suffix
                + "@sup2i.test",
            "Scoped",
            prefix
        );

        UUID roleId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO roles (
                id,
                code,
                name,
                is_system
            )
            VALUES (?, ?, ?, FALSE)
            """,
            roleId,
            "SCOPED_ROLE_" + suffix,
            "Scoped E2E Role"
        );

        UUID candidatePermissionId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO permissions (
                id,
                code,
                description
            )
            VALUES (
                ?,
                'canteen.menu.read',
                'Scoped E2E permission'
            )
            ON CONFLICT (code)
            DO NOTHING
            """,
            candidatePermissionId
        );

        UUID permissionId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM permissions
                WHERE code = 'canteen.menu.read'
                """,
                UUID.class
            );

        jdbcTemplate.update(
            """
            INSERT INTO role_permissions (
                role_id,
                permission_id
            )
            VALUES (?, ?)
            """,
            roleId,
            permissionId
        );

        jdbcTemplate.update(
            """
            INSERT INTO user_roles (
                id,
                user_id,
                role_id,
                campus_id,
                location_id
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            userId,
            roleId,
            scopeCampusId,
            scopeLocationId
        );

        User user =
            userRepository
                .findById(
                    userId
                )
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "scoped-e2e",
                InetAddress
                    .getLoopbackAddress()
            );

        return new Actor(
            userId,
            tokens.accessToken()
        );
    }

    private record Actor(
        UUID userId,
        String accessToken
    ) {
    }
}