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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the V062 permission seed (product.write, category.write,
 * catalog.read) actually unblocks real accounts on a representative
 * sample of the ~60 catalog/inventory endpoints that referenced these
 * permissions before they were ever seeded.
 */
@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class RbacCatalogInventoryAccessE2EIntegrationTest {

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

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "RBAC"
            );
    }

    // =========================================================
    // 01 - READ: catalog.read (SNACK_MANAGER, SYSTEM_ADMIN,
    //      DIRECTION, ADMINISTRATION all granted it; no-role denied)
    // =========================================================

    @Test
    void catalogReadIsUnblockedForEveryGrantedRole()
        throws Exception {

        Actor snackManager =
            insertActor(
                "READ1",
                "SNACK_MANAGER"
            );

        Actor systemAdmin =
            insertActor(
                "READ2",
                "SYSTEM_ADMIN"
            );

        Actor direction =
            insertActor(
                "READ3",
                "DIRECTION"
            );

        Actor administration =
            insertActor(
                "READ4",
                "ADMINISTRATION"
            );

        Actor noRole =
            insertActor(
                "READ5"
            );

        for (
            Actor actor
            : new Actor[] {
                snackManager,
                systemAdmin,
                direction,
                administration
            }
        ) {

            mockMvc.perform(
                    get(
                        "/api/v1/catalog/categories"
                    )
                        .header(
                            "Authorization",
                            bearer(actor)
                        )
                )
                .andExpect(
                    status().isOk()
                );
        }

        mockMvc.perform(
                get(
                    "/api/v1/catalog/categories"
                )
                    .header(
                        "Authorization",
                        bearer(noRole)
                    )
            )
            .andExpect(
                status().isForbidden()
            );
    }

    // =========================================================
    // 02 - WRITE (category.write): SNACK_MANAGER/SYSTEM_ADMIN
    //      granted; DIRECTION (read-only per matrix) denied
    // =========================================================

    @Test
    void categoryWriteIsUnblockedForOperationalRoles()
        throws Exception {

        Actor snackManager =
            insertActor(
                "CATW1",
                "SNACK_MANAGER"
            );

        Actor direction =
            insertActor(
                "CATW2",
                "DIRECTION"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/categories"
                )
                    .header(
                        "Authorization",
                        bearer(snackManager)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "name": "RBAC Test Category",
                          "displayOrder": 0
                        }
                        """
                    )
            )
            .andExpect(
                status().isCreated()
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/categories"
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "name": "Should Be Forbidden",
                          "displayOrder": 0
                        }
                        """
                    )
            )
            .andExpect(
                status().isForbidden()
            );
    }

    // =========================================================
    // 03 - WRITE (product.write, catalog side): SYSTEM_ADMIN granted
    // =========================================================

    @Test
    void productWriteIsUnblockedOnCatalogSide()
        throws Exception {

        Actor systemAdmin =
            insertActor(
                "PRODW1",
                "SYSTEM_ADMIN"
            );

        UUID categoryId =
            insertCategory(
                "PRODW1"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products"
                )
                    .header(
                        "Authorization",
                        bearer(systemAdmin)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "categoryId": "%s",
                          "sku": "RBAC-SKU-001",
                          "name": "RBAC Test Product",
                          "productType": "PACKAGED",
                          "basePrice": 10.00,
                          "taxRate": 0.00
                        }
                        """.formatted(
                            categoryId
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            );
    }

    // =========================================================
    // 04 - ADMIN (product.write, inventory side): SNACK_MANAGER granted
    // =========================================================

    @Test
    void productWriteIsUnblockedOnInventorySide()
        throws Exception {

        Actor snackManager =
            insertActor(
                "INVW1",
                "SNACK_MANAGER"
            );

        Actor noRole =
            insertActor(
                "INVW2"
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/stock-locations"
                )
                    .header(
                        "Authorization",
                        bearer(snackManager)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/stock-locations"
                )
                    .header(
                        "Authorization",
                        bearer(noRole)
                    )
            )
            .andExpect(
                status().isForbidden()
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

    private Actor insertActor(
        String prefix,
        String... roleCodes
    ) {

        UUID userId =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

        String email =
            "rbac-e2e-"
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
            organizationId,
            email,
            "Rbac",
            prefix
        );

        for (
            String roleCode
            : roleCodes
        ) {

            UUID roleId =
                jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM roles
                    WHERE code = ?
                    """,
                    UUID.class,
                    roleCode
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

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "rbac-e2e-"
                    + prefix,
                InetAddress
                    .getLoopbackAddress()
            );

        return new Actor(
            userId,
            tokens.accessToken()
        );
    }

    private UUID insertCategory(
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO categories (
                id,
                organization_id,
                name,
                slug,
                display_order,
                is_active
            )
            VALUES (?, ?, ?, ?, 0, TRUE)
            """,
            id,
            organizationId,
            prefix + " Category",
            "category-" + randomSuffix()
        );

        return id;
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
