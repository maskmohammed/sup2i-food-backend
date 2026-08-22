package com.sup2i.food.catalog;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.config.SecurityProperties;
import com.sup2i.food.security.service.AuthenticationTokens;
import com.sup2i.food.security.service.RefreshTokenService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class CatalogMenuE2EIntegrationTest {

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

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private SecurityProperties securityProperties;

    private UUID organizationId;
    private UUID userId;
    private String email;
    private String sessionId;

    @BeforeEach
    void seedTenantAndSession() {

        String suffix =
            randomSuffix();

        organizationId =
            UUID.randomUUID();

        userId =
            UUID.randomUUID();

        email =
            "catalog-menu-"
                + suffix
                + "@sup2i.test";

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
            organizationId,
            "Catalog Menu " + suffix,
            "MENU" + suffix
        );

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
            "Catalog",
            "Menu"
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "catalog-menu-e2e",
                InetAddress.getLoopbackAddress()
            );

        Jwt jwt =
            jwtDecoder.decode(
                tokens.accessToken()
            );

        sessionId =
            jwt.getClaimAsString(
                "sid"
            );

        assertThat(sessionId)
            .isNotBlank();
    }

    // =========================================================
    // SECURITY
    // =========================================================

    @Test
    void menuWriteRequiresProductWritePermission()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-RBAC-W"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/menu",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "pricingMode": "FIXED",
                          "active": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PERMISSION_DENIED")
            );
    }

    @Test
    void menuReadRequiresCatalogReadPermission()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-RBAC-R"
            );

        insertMenu(
            menuProductId,
            "FIXED",
            true
        );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/menu",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
            )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PERMISSION_DENIED")
            );
    }

    // =========================================================
    // MENU
    // =========================================================

    @Test
    void upsertMenuCreatesThenUpdatesSingleRow()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-UPSERT"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/menu",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "pricingMode": "FIXED",
                          "description": "  Menu étudiant  ",
                          "active": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.pricingMode")
                    .value("FIXED")
            )
            .andExpect(
                jsonPath("$.description")
                    .value("Menu étudiant")
            )
            .andExpect(
                jsonPath("$.active")
                    .value(true)
            );

        UUID firstId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM menus
                WHERE product_id = ?
                """,
                UUID.class,
                menuProductId
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/menu",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "pricingMode": "CALCULATED",
                          "description": "  Calculated menu  ",
                          "active": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.id")
                    .value(firstId.toString())
            )
            .andExpect(
                jsonPath("$.pricingMode")
                    .value("CALCULATED")
            )
            .andExpect(
                jsonPath("$.description")
                    .value("Calculated menu")
            );

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM menus
                WHERE product_id = ?
                """,
                Long.class,
                menuProductId
            );

        assertThat(count)
            .isEqualTo(1L);
    }

    @Test
    void invalidPricingModeReturnsValidationError()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-BAD-MODE"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/menu",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "pricingMode": "UNKNOWN_MODE"
                        }
                        """
                    )
            )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_ERROR")
            );
    }

    // =========================================================
    // SECTIONS
    // =========================================================

    @Test
    void createSectionPersistsDefaultsAndTrimsCode()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-SECTION"
            );

        insertMenu(
            menuProductId,
            "FIXED",
            true
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/menu/sections",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "code": "  DRINK  ",
                          "name": "Boisson",
                          "displayOrder": 2
                        }
                        """
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("DRINK")
            )
            .andExpect(
                jsonPath("$.name")
                    .value("Boisson")
            )
            .andExpect(
                jsonPath("$.minSelect")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.maxSelect")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.active")
                    .value(true)
            );

        Integer minSelect =
            jdbcTemplate.queryForObject(
                """
                SELECT min_select
                FROM menu_sections
                WHERE menu_id = (
                    SELECT id
                    FROM menus
                    WHERE product_id = ?
                )
                  AND code = 'DRINK'
                """,
                Integer.class,
                menuProductId
            );

        assertThat(minSelect)
            .isEqualTo(1);
    }

    @Test
    void duplicateSectionCodeReturnsConflict()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-SECTION-DUP"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        insertMenuSection(
            menuId,
            "DRINK",
            "Boisson",
            1,
            1,
            0,
            true
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/menu/sections",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "code": "DRINK",
                          "name": "Autre boisson",
                          "minSelect": 0,
                          "maxSelect": 1,
                          "displayOrder": 1
                        }
                        """
                    )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );
    }

    @Test
    void invalidSectionSelectionRangeReturnsValidationError()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-SECTION-RANGE"
            );

        insertMenu(
            menuProductId,
            "FIXED",
            true
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/menu/sections",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "name": "Invalid",
                          "minSelect": 3,
                          "maxSelect": 1,
                          "displayOrder": 0
                        }
                        """
                    )
            )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_ERROR")
            );
    }

    // =========================================================
    // ITEMS
    // =========================================================

    @Test
    void createItemWithVariantPersistsRelationshipAndDefaults()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-ITEM"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID sectionId =
            insertMenuSection(
                menuId,
                "MAIN",
                "Plat",
                1,
                1,
                0,
                true
            );

        UUID itemProductId =
            insertOwnedProduct(
                "ITEM-PRODUCT",
                "Burger"
            );

        UUID variantId =
            insertVariant(
                itemProductId,
                "Large",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/menu/sections/{sectionId}/items",
                    menuProductId,
                    sectionId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "productId": "%s",
                          "variantId": "%s",
                          "displayOrder": 3
                        }
                        """.formatted(
                            itemProductId,
                            variantId
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.productId")
                    .value(itemProductId.toString())
            )
            .andExpect(
                jsonPath("$.variantId")
                    .value(variantId.toString())
            )
            .andExpect(
                jsonPath("$.quantity")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.priceDelta")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.defaultItem")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.active")
                    .value(true)
            );

        UUID persistedVariant =
            jdbcTemplate.queryForObject(
                """
                SELECT variant_id
                FROM menu_items
                WHERE menu_section_id = ?
                  AND product_id = ?
                """,
                UUID.class,
                sectionId,
                itemProductId
            );

        assertThat(persistedVariant)
            .isEqualTo(variantId);
    }

    @Test
    void zeroQuantityReturnsValidationError()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-QTY"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID sectionId =
            insertMenuSection(
                menuId,
                "QTY",
                "Quantity",
                0,
                1,
                0,
                true
            );

        UUID itemProductId =
            insertOwnedProduct(
                "QTY-ITEM",
                "Quantity Item"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/menu/sections/{sectionId}/items",
                    menuProductId,
                    sectionId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "productId": "%s",
                          "quantity": 0,
                          "displayOrder": 0
                        }
                        """.formatted(
                            itemProductId
                        )
                    )
            )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_ERROR")
            );
    }

    @Test
    void itemVariantMustBelongToSelectedProduct()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-WRONG-VARIANT"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID sectionId =
            insertMenuSection(
                menuId,
                "VARIANT",
                "Variant",
                0,
                1,
                0,
                true
            );

        UUID productA =
            insertOwnedProduct(
                "ITEM-A",
                "Item A"
            );

        UUID productB =
            insertOwnedProduct(
                "ITEM-B",
                "Item B"
            );

        UUID variantB =
            insertVariant(
                productB,
                "Variant B",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/menu/sections/{sectionId}/items",
                    menuProductId,
                    sectionId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "productId": "%s",
                          "variantId": "%s",
                          "displayOrder": 0
                        }
                        """.formatted(
                            productA,
                            variantB
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );
    }

    @Test
    void crossTenantItemProductIsRejected()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-FOREIGN-ITEM"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID sectionId =
            insertMenuSection(
                menuId,
                "FOREIGN",
                "Foreign",
                0,
                1,
                0,
                true
            );

        UUID otherOrganization =
            insertOrganization(
                "OTHERITEM"
            );

        UUID foreignCategory =
            insertCategory(
                otherOrganization,
                "Foreign Item Category",
                true
            );

        UUID foreignProduct =
            insertProduct(
                otherOrganization,
                foreignCategory,
                "FOREIGN-ITEM",
                "Foreign Item",
                true,
                "PACKAGED"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/menu/sections/{sectionId}/items",
                    menuProductId,
                    sectionId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "productId": "%s",
                          "displayOrder": 0
                        }
                        """.formatted(
                            foreignProduct
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );
    }

    @Test
    void sectionMustBelongToSelectedMenu()
        throws Exception {

        UUID menuProductA =
            insertOwnedMenuProduct(
                "MENU-A"
            );

        UUID menuProductB =
            insertOwnedMenuProduct(
                "MENU-B"
            );

        insertMenu(
            menuProductA,
            "FIXED",
            true
        );

        UUID menuB =
            insertMenu(
                menuProductB,
                "FIXED",
                true
            );

        UUID sectionB =
            insertMenuSection(
                menuB,
                "B",
                "Section B",
                0,
                1,
                0,
                true
            );

        UUID itemProduct =
            insertOwnedProduct(
                "SECTION-ITEM",
                "Section Item"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/menu/sections/{sectionId}/items",
                    menuProductA,
                    sectionB
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "productId": "%s",
                          "displayOrder": 0
                        }
                        """.formatted(
                            itemProduct
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );
    }

    // =========================================================
    // ADMIN LIFECYCLE
    // =========================================================

    @Test
    void upsertMenuReturnsExistingSectionsAndItems()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-UPSERT-TREE"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID sectionId =
            insertMenuSection(
                menuId,
                "MAIN",
                "Main",
                1,
                1,
                0,
                true
            );

        UUID itemProductId =
            insertOwnedProduct(
                "UPSERT-TREE-ITEM",
                "Existing Item"
            );

        insertMenuItem(
            sectionId,
            itemProductId,
            null,
            0,
            true
        );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/menu",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "pricingMode": "CALCULATED",
                          "description": "Updated tree",
                          "active": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.pricingMode")
                    .value("CALCULATED")
            )
            .andExpect(
                jsonPath("$.sections.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.sections[0].id")
                    .value(sectionId.toString())
            )
            .andExpect(
                jsonPath("$.sections[0].items.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.sections[0].items[0].productId")
                    .value(itemProductId.toString())
            );
    }

    @Test
    void updateSectionChangesFieldsAndCanDeactivate()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-SECTION-UPDATE"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID sectionId =
            insertMenuSection(
                menuId,
                "OLD",
                "Old Section",
                1,
                1,
                0,
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/menu/sections/{sectionId}",
                    menuProductId,
                    sectionId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "code": "  UPDATED  ",
                          "name": "  Updated Section  ",
                          "minSelect": 0,
                          "maxSelect": 2,
                          "displayOrder": 5,
                          "active": false
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("UPDATED")
            )
            .andExpect(
                jsonPath("$.name")
                    .value("Updated Section")
            )
            .andExpect(
                jsonPath("$.minSelect")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.maxSelect")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.displayOrder")
                    .value(5)
            )
            .andExpect(
                jsonPath("$.active")
                    .value(false)
            );

        Boolean active =
            jdbcTemplate.queryForObject(
                """
                SELECT is_active
                FROM menu_sections
                WHERE id = ?
                """,
                Boolean.class,
                sectionId
            );

        assertThat(active)
            .isFalse();

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/menu",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.sections.length()")
                    .value(0)
            );
    }

    @Test
    void updateSectionCannotCrossMenuBoundary()
        throws Exception {

        UUID productA =
            insertOwnedMenuProduct(
                "MENU-SECTION-A"
            );

        UUID productB =
            insertOwnedMenuProduct(
                "MENU-SECTION-B"
            );

        insertMenu(
            productA,
            "FIXED",
            true
        );

        UUID menuB =
            insertMenu(
                productB,
                "FIXED",
                true
            );

        UUID sectionB =
            insertMenuSection(
                menuB,
                "FOREIGN",
                "Foreign Section",
                0,
                1,
                0,
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/menu/sections/{sectionId}",
                    productA,
                    sectionB
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "code": "X",
                          "name": "Wrong",
                          "minSelect": 0,
                          "maxSelect": 1,
                          "displayOrder": 0,
                          "active": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );
    }

    @Test
    void updateItemChangesRelationshipAndCanDeactivate()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-ITEM-UPDATE"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID sectionId =
            insertMenuSection(
                menuId,
                "MAIN",
                "Main",
                0,
                2,
                0,
                true
            );

        UUID oldProduct =
            insertOwnedProduct(
                "ITEM-OLD",
                "Old Item"
            );

        UUID newProduct =
            insertOwnedProduct(
                "ITEM-NEW",
                "New Item"
            );

        UUID newVariant =
            insertVariant(
                newProduct,
                "Large",
                true
            );

        UUID itemId =
            insertMenuItem(
                sectionId,
                oldProduct,
                null,
                0,
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/menu/sections/{sectionId}/items/{itemId}",
                    menuProductId,
                    sectionId,
                    itemId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "productId": "%s",
                          "variantId": "%s",
                          "quantity": 2.500,
                          "priceDelta": 1.25,
                          "defaultItem": true,
                          "active": false,
                          "displayOrder": 4
                        }
                        """.formatted(
                            newProduct,
                            newVariant
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.productId")
                    .value(newProduct.toString())
            )
            .andExpect(
                jsonPath("$.variantId")
                    .value(newVariant.toString())
            )
            .andExpect(
                jsonPath("$.quantity")
                    .value(2.5)
            )
            .andExpect(
                jsonPath("$.priceDelta")
                    .value(1.25)
            )
            .andExpect(
                jsonPath("$.defaultItem")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.active")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.displayOrder")
                    .value(4)
            );

        Boolean active =
            jdbcTemplate.queryForObject(
                """
                SELECT is_active
                FROM menu_items
                WHERE id = ?
                """,
                Boolean.class,
                itemId
            );

        assertThat(active)
            .isFalse();

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/menu",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.sections[0].items.length()")
                    .value(0)
            );
    }

    @Test
    void updateItemCannotCrossSectionBoundary()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-ITEM-BOUNDARY"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID sectionA =
            insertMenuSection(
                menuId,
                "A",
                "Section A",
                0,
                1,
                0,
                true
            );

        UUID sectionB =
            insertMenuSection(
                menuId,
                "B",
                "Section B",
                0,
                1,
                1,
                true
            );

        UUID itemProduct =
            insertOwnedProduct(
                "BOUNDARY-ITEM",
                "Boundary Item"
            );

        UUID itemB =
            insertMenuItem(
                sectionB,
                itemProduct,
                null,
                0,
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/menu/sections/{sectionId}/items/{itemId}",
                    menuProductId,
                    sectionA,
                    itemB
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "productId": "%s",
                          "quantity": 1,
                          "priceDelta": 0,
                          "defaultItem": false,
                          "active": true,
                          "displayOrder": 0
                        }
                        """.formatted(
                            itemProduct
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );
    }

    @Test
    void menuLifecycleUpdateRequiresProductWritePermission()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-LIFECYCLE-RBAC"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID sectionId =
            insertMenuSection(
                menuId,
                "RBAC",
                "RBAC Section",
                0,
                1,
                0,
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/menu/sections/{sectionId}",
                    menuProductId,
                    sectionId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "code": "RBAC",
                          "name": "RBAC Section",
                          "minSelect": 0,
                          "maxSelect": 1,
                          "displayOrder": 0,
                          "active": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PERMISSION_DENIED")
            );
    }
    // =========================================================
    // PRODUCT SUBSTITUTIONS
    // =========================================================

    @Test
    void upsertSubstitutionCreatesThenUpdatesSingleRow()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "SUB-SOURCE",
                "Source Product"
            );

        UUID substituteId =
            insertOwnedProduct(
                "SUB-TARGET",
                "Target Product"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/substitutions/{substituteProductId}",
                    productId,
                    substituteId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "priority": 2,
                          "active": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.productId")
                    .value(productId.toString())
            )
            .andExpect(
                jsonPath("$.substituteProductId")
                    .value(substituteId.toString())
            )
            .andExpect(
                jsonPath("$.priority")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.active")
                    .value(true)
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/substitutions/{substituteProductId}",
                    productId,
                    substituteId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "priority": 5,
                          "active": false
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.priority")
                    .value(5)
            )
            .andExpect(
                jsonPath("$.active")
                    .value(false)
            );

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM product_substitutions
                WHERE product_id = ?
                  AND substitute_product_id = ?
                """,
                Long.class,
                productId,
                substituteId
            );

        assertThat(count)
            .isEqualTo(1L);

        mockMvc.perform(
                get(
                    "/api/v1/admin/products/{productId}/substitutions",
                    productId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$[0].active")
                    .value(false)
            );
    }

    @Test
    void productCannotSubstituteItself()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "SUB-SELF",
                "Self Product"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/substitutions/{substituteProductId}",
                    productId,
                    productId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "priority": 0,
                          "active": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_ERROR")
            );
    }

    @Test
    void foreignSubstituteProductIsRejected()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "SUB-LOCAL",
                "Local Product"
            );

        UUID otherOrganization =
            insertOrganization(
                "SUB-FOREIGN"
            );

        UUID foreignCategory =
            insertCategory(
                otherOrganization,
                "Foreign Substitution Category",
                true
            );

        UUID foreignProduct =
            insertProduct(
                otherOrganization,
                foreignCategory,
                "SUB-FOREIGN",
                "Foreign Substitute",
                true,
                "PACKAGED"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/substitutions/{substituteProductId}",
                    productId,
                    foreignProduct
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "priority": 0,
                          "active": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );
    }

    @Test
    void substitutionWriteRequiresProductWritePermission()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "SUB-RBAC-SOURCE",
                "RBAC Source"
            );

        UUID substituteId =
            insertOwnedProduct(
                "SUB-RBAC-TARGET",
                "RBAC Target"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/substitutions/{substituteProductId}",
                    productId,
                    substituteId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "priority": 0,
                          "active": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PERMISSION_DENIED")
            );
    }

    @Test
    void substitutionReadRequiresCatalogReadPermission()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "SUB-READ-SOURCE",
                "Read Source"
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/substitutions",
                    productId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
            )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PERMISSION_DENIED")
            );
    }

    @Test
    void catalogSubstitutionsFilterUnavailableAndOrderByPriority()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "SUB-CATALOG-SOURCE",
                "Catalog Source"
            );

        UUID first =
            insertOwnedProduct(
                "SUB-FIRST",
                "Alpha Substitute"
            );

        UUID second =
            insertOwnedProduct(
                "SUB-SECOND",
                "Beta Substitute"
            );

        UUID inactiveLink =
            insertOwnedProduct(
                "SUB-INACTIVE-LINK",
                "Inactive Link"
            );

        UUID inactiveProduct =
            insertOwnedProduct(
                "SUB-INACTIVE-PRODUCT",
                "Inactive Product"
            );

        UUID inactiveCategoryProduct =
            insertOwnedProduct(
                "SUB-INACTIVE-CATEGORY",
                "Inactive Category Product"
            );

        jdbcTemplate.update(
            """
            INSERT INTO product_substitutions (
                product_id,
                substitute_product_id,
                priority,
                is_active
            )
            VALUES (?, ?, 1, TRUE)
            """,
            productId,
            first
        );

        jdbcTemplate.update(
            """
            INSERT INTO product_substitutions (
                product_id,
                substitute_product_id,
                priority,
                is_active
            )
            VALUES (?, ?, 2, TRUE)
            """,
            productId,
            second
        );

        jdbcTemplate.update(
            """
            INSERT INTO product_substitutions (
                product_id,
                substitute_product_id,
                priority,
                is_active
            )
            VALUES (?, ?, 0, FALSE)
            """,
            productId,
            inactiveLink
        );

        jdbcTemplate.update(
            """
            INSERT INTO product_substitutions (
                product_id,
                substitute_product_id,
                priority,
                is_active
            )
            VALUES (?, ?, 0, TRUE)
            """,
            productId,
            inactiveProduct
        );

        jdbcTemplate.update(
            """
            UPDATE products
            SET is_active = FALSE
            WHERE id = ?
            """,
            inactiveProduct
        );

        jdbcTemplate.update(
            """
            INSERT INTO product_substitutions (
                product_id,
                substitute_product_id,
                priority,
                is_active
            )
            VALUES (?, ?, 0, TRUE)
            """,
            productId,
            inactiveCategoryProduct
        );

        jdbcTemplate.update(
            """
            UPDATE categories
            SET is_active = FALSE
            WHERE id = (
                SELECT category_id
                FROM products
                WHERE id = ?
            )
            """,
            inactiveCategoryProduct
        );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/substitutions",
                    productId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(2)
            )
            .andExpect(
                jsonPath("$[0].substituteProductId")
                    .value(first.toString())
            )
            .andExpect(
                jsonPath("$[0].priority")
                    .value(1)
            )
            .andExpect(
                jsonPath("$[1].substituteProductId")
                    .value(second.toString())
            )
            .andExpect(
                jsonPath("$[1].priority")
                    .value(2)
            );
    }

    @Test
    void inactiveSourceProductSubstitutionsReturnProductUnavailable()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "SUB-INACTIVE-SOURCE",
                "Inactive Source"
            );

        jdbcTemplate.update(
            """
            UPDATE products
            SET is_active = FALSE
            WHERE id = ?
            """,
            productId
        );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/substitutions",
                    productId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
            )
            .andExpect(
                status().isUnprocessableContent()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PRODUCT_UNAVAILABLE")
            );
    }
    // =========================================================
    // V052 DATABASE INVARIANT
    // =========================================================

    @Test
    void databaseRejectsVariantProductMismatch() {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-DB-FK"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID sectionId =
            insertMenuSection(
                menuId,
                "DB-FK",
                "DB FK",
                0,
                1,
                0,
                true
            );

        UUID productA =
            insertOwnedProduct(
                "DB-A",
                "DB Product A"
            );

        UUID productB =
            insertOwnedProduct(
                "DB-B",
                "DB Product B"
            );

        UUID variantB =
            insertVariant(
                productB,
                "DB Variant B",
                true
            );

        assertThatThrownBy(() ->
            jdbcTemplate.update(
                """
                INSERT INTO menu_items (
                    id,
                    menu_section_id,
                    product_id,
                    variant_id,
                    quantity,
                    price_delta,
                    is_default,
                    is_active,
                    display_order
                )
                VALUES (?, ?, ?, ?, 1, 0, FALSE, TRUE, 0)
                """,
                UUID.randomUUID(),
                sectionId,
                productA,
                variantB
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    // =========================================================
    // CATALOG HIERARCHY / ORDERING
    // =========================================================

    @Test
    void catalogMenuOrdersSectionsAndItems()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-ORDER"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "CALCULATED",
                true
            );

        UUID secondSection =
            insertMenuSection(
                menuId,
                "SECOND",
                "Second",
                0,
                2,
                2,
                true
            );

        UUID firstSection =
            insertMenuSection(
                menuId,
                "FIRST",
                "First",
                1,
                2,
                1,
                true
            );

        UUID productZulu =
            insertOwnedProduct(
                "ORDER-Z",
                "Zulu"
            );

        UUID productAlpha =
            insertOwnedProduct(
                "ORDER-A",
                "Alpha"
            );

        UUID productSecond =
            insertOwnedProduct(
                "ORDER-SECOND",
                "Second Product"
            );

        insertMenuItem(
            firstSection,
            productZulu,
            null,
            2,
            true
        );

        insertMenuItem(
            firstSection,
            productAlpha,
            null,
            1,
            true
        );

        insertMenuItem(
            secondSection,
            productSecond,
            null,
            0,
            true
        );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/menu",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.pricingMode")
                    .value("CALCULATED")
            )
            .andExpect(
                jsonPath("$.sections[0].name")
                    .value("First")
            )
            .andExpect(
                jsonPath("$.sections[1].name")
                    .value("Second")
            )
            .andExpect(
                jsonPath("$.sections[0].items[0].productName")
                    .value("Alpha")
            )
            .andExpect(
                jsonPath("$.sections[0].items[1].productName")
                    .value("Zulu")
            );
    }

    @Test
    void inactiveSectionAndItemAreHidden()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-HIDDEN-STRUCTURE"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID activeSection =
            insertMenuSection(
                menuId,
                "ACTIVE",
                "Active Section",
                0,
                3,
                0,
                true
            );

        UUID inactiveSection =
            insertMenuSection(
                menuId,
                "INACTIVE",
                "Hidden Section",
                0,
                1,
                1,
                false
            );

        UUID visibleProduct =
            insertOwnedProduct(
                "VISIBLE-ITEM",
                "Visible Item"
            );

        UUID hiddenItemProduct =
            insertOwnedProduct(
                "HIDDEN-ITEM",
                "Hidden Item"
            );

        UUID hiddenSectionProduct =
            insertOwnedProduct(
                "HIDDEN-SECTION-ITEM",
                "Hidden Section Item"
            );

        insertMenuItem(
            activeSection,
            visibleProduct,
            null,
            0,
            true
        );

        insertMenuItem(
            activeSection,
            hiddenItemProduct,
            null,
            1,
            false
        );

        insertMenuItem(
            inactiveSection,
            hiddenSectionProduct,
            null,
            0,
            true
        );

        String body =
            mockMvc.perform(
                    get(
                        "/api/v1/catalog/products/{productId}/menu",
                        menuProductId
                    )
                        .header(
                            "Authorization",
                            bearer("catalog.read")
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
            .contains("Active Section")
            .contains("Visible Item")
            .doesNotContain("Hidden Section")
            .doesNotContain("Hidden Item")
            .doesNotContain("Hidden Section Item");
    }

    @Test
    void inactiveComponentProductAndVariantAreHidden()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-HIDDEN-COMPONENT"
            );

        UUID menuId =
            insertMenu(
                menuProductId,
                "FIXED",
                true
            );

        UUID sectionId =
            insertMenuSection(
                menuId,
                "COMPONENT",
                "Components",
                0,
                4,
                0,
                true
            );

        UUID visibleProduct =
            insertOwnedProduct(
                "VISIBLE-COMP",
                "Visible Component"
            );

        UUID inactiveProduct =
            insertOwnedProduct(
                "INACTIVE-COMP",
                "Inactive Component"
            );

        jdbcTemplate.update(
            """
            UPDATE products
            SET is_active = FALSE
            WHERE id = ?
            """,
            inactiveProduct
        );

        UUID variantProduct =
            insertOwnedProduct(
                "VARIANT-COMP",
                "Variant Component"
            );

        UUID activeVariant =
            insertVariant(
                variantProduct,
                "Active Variant",
                true
            );

        UUID inactiveVariant =
            insertVariant(
                variantProduct,
                "Inactive Variant",
                false
            );

        insertMenuItem(
            sectionId,
            visibleProduct,
            null,
            0,
            true
        );

        insertMenuItem(
            sectionId,
            inactiveProduct,
            null,
            1,
            true
        );

        insertMenuItem(
            sectionId,
            variantProduct,
            activeVariant,
            2,
            true
        );

        insertMenuItem(
            sectionId,
            variantProduct,
            inactiveVariant,
            3,
            true
        );

        String body =
            mockMvc.perform(
                    get(
                        "/api/v1/catalog/products/{productId}/menu",
                        menuProductId
                    )
                        .header(
                            "Authorization",
                            bearer("catalog.read")
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
            .contains("Visible Component")
            .contains("Active Variant")
            .doesNotContain("Inactive Component")
            .doesNotContain("Inactive Variant");
    }

    // =========================================================
    // AVAILABILITY / TENANT
    // =========================================================

    @Test
    void inactiveMenuReturnsProductUnavailable()
        throws Exception {

        UUID menuProductId =
            insertOwnedMenuProduct(
                "MENU-INACTIVE"
            );

        insertMenu(
            menuProductId,
            "FIXED",
            false
        );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/menu",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
            )
            .andExpect(
                status().isUnprocessableContent()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PRODUCT_UNAVAILABLE")
            );
    }

    @Test
    void inactiveMenuProductReturnsProductUnavailable()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Inactive Menu Category",
                true
            );

        UUID menuProductId =
            insertProduct(
                organizationId,
                categoryId,
                "MENU-INACTIVE-PRODUCT",
                "Inactive Menu Product",
                false,
                "MENU"
            );

        insertMenu(
            menuProductId,
            "FIXED",
            true
        );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/menu",
                    menuProductId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
            )
            .andExpect(
                status().isUnprocessableContent()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PRODUCT_UNAVAILABLE")
            );
    }

    @Test
    void menuCannotCrossTenantBoundary()
        throws Exception {

        UUID otherOrganization =
            insertOrganization(
                "FOREIGNMENU"
            );

        UUID foreignCategory =
            insertCategory(
                otherOrganization,
                "Foreign Menu Category",
                true
            );

        UUID foreignMenuProduct =
            insertProduct(
                otherOrganization,
                foreignCategory,
                "FOREIGN-MENU",
                "Foreign Menu",
                true,
                "MENU"
            );

        insertMenu(
            foreignMenuProduct,
            "FIXED",
            true
        );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/menu",
                    foreignMenuProduct
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );
    }

    // =========================================================
    // JWT
    // =========================================================

    private String bearer(
        String... permissions
    ) {

        return "Bearer "
            + token(permissions);
    }

    private String token(
        String... permissions
    ) {

        Instant now =
            Instant.now();

        JwtClaimsSet claims =
            JwtClaimsSet.builder()
                .issuer(
                    securityProperties
                        .jwt()
                        .issuer()
                )
                .subject(
                    userId.toString()
                )
                .issuedAt(now)
                .expiresAt(
                    now.plusSeconds(600)
                )
                .id(
                    UUID.randomUUID()
                        .toString()
                )
                .claim(
                    "sid",
                    sessionId
                )
                .claim(
                    "email",
                    email
                )
                .claim(
                    "roles",
                    List.of()
                )
                .claim(
                    "permissions",
                    List.of(permissions)
                )
                .claim(
                    "role_scopes",
                    List.of()
                )
                .build();

        return jwtEncoder
            .encode(
                JwtEncoderParameters.from(
                    claims
                )
            )
            .getTokenValue();
    }

    // =========================================================
    // DATABASE FIXTURES
    // =========================================================

    private UUID insertOwnedMenuProduct(
        String prefix
    ) {

        UUID categoryId =
            insertCategory(
                organizationId,
                prefix + " Category",
                true
            );

        return insertProduct(
            organizationId,
            categoryId,
            prefix,
            prefix + " Product",
            true,
            "MENU"
        );
    }

    private UUID insertOwnedProduct(
        String prefix,
        String name
    ) {

        UUID categoryId =
            insertCategory(
                organizationId,
                prefix + " Category",
                true
            );

        return insertProduct(
            organizationId,
            categoryId,
            prefix,
            name,
            true,
            "PACKAGED"
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
            "ORG" + randomSuffix()
        );

        return id;
    }

    private UUID insertCategory(
        UUID tenantId,
        String name,
        boolean active
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
            VALUES (?, ?, ?, ?, 0, ?)
            """,
            id,
            tenantId,
            name,
            "category-" + randomSuffix(),
            active
        );

        return id;
    }

    private UUID insertProduct(
        UUID tenantId,
        UUID categoryId,
        String skuPrefix,
        String name,
        boolean active,
        String productType
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO products (
                id,
                organization_id,
                category_id,
                sku,
                name,
                product_type,
                base_price,
                tax_rate,
                preparation_minutes,
                track_stock,
                is_prepared,
                is_active
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                10.00,
                0.00,
                0,
                TRUE,
                FALSE,
                ?
            )
            """,
            id,
            tenantId,
            categoryId,
            skuPrefix + "-" + randomSuffix(),
            name,
            productType,
            active
        );

        return id;
    }

    private UUID insertVariant(
        UUID productId,
        String name,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO product_variants (
                id,
                product_id,
                name,
                sku,
                price_delta,
                is_active,
                display_order
            )
            VALUES (?, ?, ?, ?, 0, ?, 0)
            """,
            id,
            productId,
            name,
            "VAR-" + randomSuffix(),
            active
        );

        return id;
    }

    private UUID insertMenu(
        UUID productId,
        String pricingMode,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO menus (
                id,
                product_id,
                pricing_mode,
                description,
                is_active
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            id,
            productId,
            pricingMode,
            "Test menu",
            active
        );

        return id;
    }

    private UUID insertMenuSection(
        UUID menuId,
        String code,
        String name,
        int minSelect,
        int maxSelect,
        int displayOrder,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO menu_sections (
                id,
                menu_id,
                code,
                name,
                min_select,
                max_select,
                display_order,
                is_active
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            menuId,
            code,
            name,
            minSelect,
            maxSelect,
            displayOrder,
            active
        );

        return id;
    }

    private UUID insertMenuItem(
        UUID sectionId,
        UUID productId,
        UUID variantId,
        int displayOrder,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO menu_items (
                id,
                menu_section_id,
                product_id,
                variant_id,
                quantity,
                price_delta,
                is_default,
                is_active,
                display_order
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                1,
                0,
                FALSE,
                ?,
                ?
            )
            """,
            id,
            sectionId,
            productId,
            variantId,
            active,
            displayOrder
        );

        return id;
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);
    }
}