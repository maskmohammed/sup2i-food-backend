package com.sup2i.food.identity;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.service.AuthenticationTokens;
import com.sup2i.food.security.service.RefreshTokenService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetAddress;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class AdminUserE2EIntegrationTest {

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

    private UUID organizationId;
    private UUID otherOrganizationId;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "ADMU"
            );

        otherOrganizationId =
            insertOrganization(
                "ADMU2"
            );
    }

    // =========================================================
    // 01 - PERMISSION PROTECTION
    // =========================================================

    @Test
    void adminEndpointsRequireUserPermission()
        throws Exception {

        Actor plain =
            insertActor(
                organizationId,
                "PLAIN",
                false
            );

        Actor admin =
            insertActor(
                organizationId,
                "ADM1",
                true
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/users"
                )
                    .header(
                        "Authorization",
                        bearer(plain)
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/users"
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isOk()
            );
    }

    // =========================================================
    // 02 - LIST AND FIND USERS
    // =========================================================

    @Test
    void listAndFindUsersReturnCorrectData()
        throws Exception {

        Actor admin =
            insertActor(
                organizationId,
                "ADM2",
                true
            );

        Actor target =
            insertActor(
                organizationId,
                "TARGET2",
                false
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/users"
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.totalElements"
                )
                    .value(2)
            )
            .andExpect(
                jsonPath(
                    "$.content[?(@.id=='"
                        + target.userId()
                        + "')].status"
                )
                    .value("ACTIVE")
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/users/{userId}",
                    target.userId()
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath("$.roles")
                    .isEmpty()
            );
    }

    // =========================================================
    // 03 - ACTIVATE / DEACTIVATE IDEMPOTENT
    // =========================================================

    @Test
    void activateAndDeactivateAreIdempotent()
        throws Exception {

        Actor admin =
            insertActor(
                organizationId,
                "ADM3",
                true
            );

        Actor target =
            insertActor(
                organizationId,
                "TARGET3",
                false
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/{userId}/deactivate",
                    target.userId()
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.user.status")
                    .value("SUSPENDED")
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/{userId}/deactivate",
                    target.userId()
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/{userId}/activate",
                    target.userId()
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.user.status")
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/{userId}/activate",
                    target.userId()
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );
    }

    // =========================================================
    // 04 - ROLE ASSIGNMENT REFLECTED IN NEXT TOKEN ISSUANCE
    // =========================================================

    @Test
    void assignAndRevokeRoleReflectedInNextTokenIssuance()
        throws Exception {

        Actor admin =
            insertActor(
                organizationId,
                "ADM4",
                true
            );

        Actor target =
            insertActor(
                organizationId,
                "TARGET4",
                false
            );

        insertRole(
            "STAFF_TEST"
        );

        String tokenBefore =
            issueToken(
                target.userId()
            );

        mockMvc.perform(
                get(
                    "/api/v1/me"
                )
                    .header(
                        "Authorization",
                        "Bearer " + tokenBefore
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.roles")
                    .isEmpty()
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/{userId}/roles",
                    target.userId()
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        { "roleCode": "STAFF_TEST" }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        String tokenAfterAssign =
            issueToken(
                target.userId()
            );

        mockMvc.perform(
                get(
                    "/api/v1/me"
                )
                    .header(
                        "Authorization",
                        "Bearer " + tokenAfterAssign
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.roles")
                    .value(
                        org.hamcrest.Matchers
                            .hasItem(
                                "STAFF_TEST"
                            )
                    )
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/{userId}/roles/{roleCode}/revoke",
                    target.userId(),
                    "STAFF_TEST"
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        String tokenAfterRevoke =
            issueToken(
                target.userId()
            );

        mockMvc.perform(
                get(
                    "/api/v1/me"
                )
                    .header(
                        "Authorization",
                        "Bearer " + tokenAfterRevoke
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.roles")
                    .value(
                        org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers
                                .hasItem(
                                    "STAFF_TEST"
                                )
                        )
                    )
            );
    }

    // =========================================================
    // 05 - REVOKE NON-ASSIGNED ROLE IS IDEMPOTENT
    // =========================================================

    @Test
    void revokeRoleIsIdempotentWhenNotAssigned()
        throws Exception {

        Actor admin =
            insertActor(
                organizationId,
                "ADM5",
                true
            );

        Actor target =
            insertActor(
                organizationId,
                "TARGET5",
                false
            );

        insertRole(
            "NEVER_ASSIGNED"
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/{userId}/roles/{roleCode}/revoke",
                    target.userId(),
                    "NEVER_ASSIGNED"
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );
    }

    // =========================================================
    // 06 - ASSIGN UNKNOWN ROLE CODE -> 404
    // =========================================================

    @Test
    void assignRoleReturns404ForUnknownRoleCode()
        throws Exception {

        Actor admin =
            insertActor(
                organizationId,
                "ADM6",
                true
            );

        Actor target =
            insertActor(
                organizationId,
                "TARGET6",
                false
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/{userId}/roles",
                    target.userId()
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        { "roleCode": "DOES_NOT_EXIST" }
                        """
                    )
            )
            .andExpect(
                status().isNotFound()
            );
    }

    // =========================================================
    // 07 - MULTI-TENANT BOUNDARY
    // =========================================================

    @Test
    void adminCannotManageUsersOutsideOwnOrganization()
        throws Exception {

        Actor admin =
            insertActor(
                organizationId,
                "ADM7",
                true
            );

        Actor foreignTarget =
            insertActor(
                otherOrganizationId,
                "FOREIGN7",
                false
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/users/{userId}",
                    foreignTarget.userId()
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isNotFound()
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/{userId}/deactivate",
                    foreignTarget.userId()
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isNotFound()
            );
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String bearer(
        Actor actor
    ) {
        return "Bearer " + actor.accessToken();
    }

    private String issueToken(
        UUID userId
    ) throws Exception {

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "admin-e2e-reissue",
                InetAddress
                    .getLoopbackAddress()
            );

        return tokens.accessToken();
    }

    private Actor insertActor(
        UUID tenantId,
        String prefix,
        boolean withAdministrationRole
    ) {

        UUID userId =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

        String email =
            "admin-e2e-"
                + prefix.toLowerCase()
                + "-"
                + suffix
                + "@sup2i.test";

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
            tenantId,
            email,
            "Admin",
            prefix
        );

        if (withAdministrationRole) {

            UUID roleId =
                jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM roles
                    WHERE code = 'ADMINISTRATION'
                    """,
                    UUID.class
                );

            jdbcTemplate.update(
                """
                INSERT INTO user_roles (
                    id,
                    user_id,
                    role_id
                )
                VALUES (?, ?, ?)
                """,
                UUID.randomUUID(),
                userId,
                roleId
            );
        }

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens;

        try {

            tokens =
                refreshTokenService.issue(
                    user,
                    "admin-e2e-"
                        + prefix,
                    InetAddress
                        .getLoopbackAddress()
                );

        } catch (Exception exception) {

            throw new RuntimeException(
                exception
            );
        }

        return new Actor(
            userId,
            tokens.accessToken()
        );
    }

    private void insertRole(
        String code
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO roles (
                id,
                code,
                name,
                is_system
            )
            VALUES (?, ?, ?, FALSE)
            ON CONFLICT (code) DO NOTHING
            """,
            UUID.randomUUID(),
            code,
            code
        );
    }

    private UUID insertOrganization(
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

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
            prefix + randomSuffix()
        );

        return id;
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);
    }

    private record Actor(
        UUID userId,
        String accessToken
    ) {
    }
}
