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

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class CatalogAdvancedE2EIntegrationTest {

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
            UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        organizationId =
            UUID.randomUUID();

        userId =
            UUID.randomUUID();

        email =
            "catalog-advanced-"
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
            "Catalog Advanced " + suffix,
            "CATA" + suffix
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
            "Advanced"
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "catalog-advanced-e2e",
                InetAddress.getLoopbackAddress()
            );

        Jwt sessionJwt =
            jwtDecoder.decode(
                tokens.accessToken()
            );

        sessionId =
            sessionJwt.getClaimAsString(
                "sid"
            );

        assertThat(sessionId)
            .isNotBlank();
    }

    // =========================================================
    // RBAC
    // =========================================================

    @Test
    void advancedWriteRequiresProductWritePermission()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "RBAC-001",
                "RBAC Product"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/variants",
                    productId
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
                          "name": "Large",
                          "sku": "RBAC-L",
                          "priceDelta": 2.00,
                          "active": true,
                          "displayOrder": 0
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
    void configurationRequiresCatalogReadPermission()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "READ-001",
                "Read Product"
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/configuration",
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

    // =========================================================
    // VARIANTS
    // =========================================================

    @Test
    void createVariantNormalizesSkuAndPersists()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "VAR-001",
                "Variant Product"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/variants",
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
                          "name": "Grande",
                          "sku": " size-large ",
                          "priceDelta": 2.50,
                          "active": true,
                          "displayOrder": 4
                        }
                        """
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.name")
                    .value("Grande")
            )
            .andExpect(
                jsonPath("$.sku")
                    .value("SIZE-LARGE")
            )
            .andExpect(
                jsonPath("$.displayOrder")
                    .value(4)
            );

        String sku =
            jdbcTemplate.queryForObject(
                """
                SELECT sku
                FROM product_variants
                WHERE product_id = ?
                  AND name = ?
                """,
                String.class,
                productId,
                "Grande"
            );

        BigDecimal priceDelta =
            jdbcTemplate.queryForObject(
                """
                SELECT price_delta
                FROM product_variants
                WHERE product_id = ?
                  AND name = ?
                """,
                BigDecimal.class,
                productId,
                "Grande"
            );

        assertThat(sku)
            .isEqualTo("SIZE-LARGE");

        assertThat(priceDelta)
            .isEqualByComparingTo("2.50");
    }

    @Test
    void configurationReturnsOnlyActiveVariants()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "VAR-002",
                "Variant Visibility"
            );

        insertVariant(
            productId,
            "Visible Variant",
            "VISIBLE-V",
            true,
            0
        );

        insertVariant(
            productId,
            "Hidden Variant",
            "HIDDEN-V",
            false,
            1
        );

        String body =
            mockMvc.perform(
                    get(
                        "/api/v1/catalog/products/{productId}/configuration",
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
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
            .contains("Visible Variant")
            .doesNotContain("Hidden Variant");
    }

    // =========================================================
    // BARCODES
    // =========================================================

    @Test
    void createBarcodeForVariantPersistsRelationship()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "BAR-001",
                "Barcode Product"
            );

        UUID variantId =
            insertVariant(
                productId,
                "Barcode Variant",
                "BAR-V",
                true,
                0
            );

        String barcode =
            uniqueBarcode();

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/barcodes",
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
                          "variantId": "%s",
                          "barcode": "%s",
                          "packQuantity": 2.000,
                          "primary": false,
                          "active": true
                        }
                        """.formatted(
                            variantId,
                            barcode
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.variantId")
                    .value(
                        variantId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.barcode")
                    .value(barcode)
            );

        UUID persistedVariant =
            jdbcTemplate.queryForObject(
                """
                SELECT variant_id
                FROM product_barcodes
                WHERE barcode = ?
                """,
                UUID.class,
                barcode
            );

        UUID persistedProduct =
            jdbcTemplate.queryForObject(
                """
                SELECT product_id
                FROM product_barcodes
                WHERE barcode = ?
                """,
                UUID.class,
                barcode
            );

        assertThat(persistedVariant)
            .isEqualTo(variantId);

        assertThat(persistedProduct)
            .isEqualTo(productId);
    }

    @Test
    void barcodeVariantMustBelongToSameProduct()
        throws Exception {

        UUID productA =
            insertOwnedProduct(
                "BAR-A",
                "Product A"
            );

        UUID productB =
            insertOwnedProduct(
                "BAR-B",
                "Product B"
            );

        UUID variantB =
            insertVariant(
                productB,
                "Variant B",
                "VAR-B",
                true,
                0
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/barcodes",
                    productA
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
                          "variantId": "%s",
                          "barcode": "%s",
                          "packQuantity": 1,
                          "primary": false,
                          "active": true
                        }
                        """.formatted(
                            variantB,
                            uniqueBarcode()
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
    void duplicateBarcodeReturnsConflict()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "BAR-DUP",
                "Duplicate Barcode"
            );

        String barcode =
            uniqueBarcode();

        insertBarcode(
            productId,
            null,
            barcode,
            false,
            true
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/barcodes",
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
                          "barcode": "%s",
                          "packQuantity": 1,
                          "primary": false,
                          "active": true
                        }
                        """.formatted(
                            barcode
                        )
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
    void secondActivePrimaryBarcodeReturnsConflict()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "BAR-PRIMARY",
                "Primary Barcode"
            );

        insertBarcode(
            productId,
            null,
            uniqueBarcode(),
            true,
            true
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/barcodes",
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
                          "barcode": "%s",
                          "packQuantity": 1,
                          "primary": true,
                          "active": true
                        }
                        """.formatted(
                            uniqueBarcode()
                        )
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

    // =========================================================
    // OPTION GROUPS / OPTIONS
    // =========================================================

    @Test
    void invalidOptionGroupSelectionRangeReturnsValidationError()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "OPT-INVALID",
                "Invalid Options"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/option-groups",
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
                          "name": "Invalid Group",
                          "minSelect": 3,
                          "maxSelect": 1,
                          "required": true,
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

    @Test
    void createOptionGroupAndOptionAppearInConfiguration()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "OPT-001",
                "Options Product"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/option-groups",
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
                          "name": "Sauce",
                          "minSelect": 1,
                          "maxSelect": 2,
                          "required": true,
                          "displayOrder": 3
                        }
                        """
                    )
            )
            .andExpect(
                status().isCreated()
            );

        UUID groupId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_option_groups
                WHERE product_id = ?
                  AND name = ?
                """,
                UUID.class,
                productId,
                "Sauce"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/option-groups/{groupId}/options",
                    productId,
                    groupId
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
                          "name": "Sauce Blanche",
                          "priceDelta": 1.50,
                          "active": true,
                          "displayOrder": 1
                        }
                        """
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.name")
                    .value("Sauce Blanche")
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/configuration",
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
                jsonPath("$.productId")
                    .value(
                        productId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.optionGroups[0].name")
                    .value("Sauce")
            )
            .andExpect(
                jsonPath(
                    "$.optionGroups[0].minSelect"
                )
                    .value(1)
            )
            .andExpect(
                jsonPath(
                    "$.optionGroups[0].maxSelect"
                )
                    .value(2)
            )
            .andExpect(
                jsonPath(
                    "$.optionGroups[0].required"
                )
                    .value(true)
            )
            .andExpect(
                jsonPath(
                    "$.optionGroups[0].options[0].name"
                )
                    .value("Sauce Blanche")
            );
    }

    @Test
    void inactiveOptionIsHiddenFromConfiguration()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "OPT-HIDDEN",
                "Hidden Option Product"
            );

        UUID groupId =
            insertOptionGroup(
                productId,
                "Accompagnement",
                0,
                2,
                false,
                0
            );

        insertOption(
            groupId,
            "Visible Option",
            true,
            0
        );

        insertOption(
            groupId,
            "Hidden Option",
            false,
            1
        );

        String body =
            mockMvc.perform(
                    get(
                        "/api/v1/catalog/products/{productId}/configuration",
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
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
            .contains("Visible Option")
            .doesNotContain("Hidden Option");
    }

    // =========================================================
    // TENANT ISOLATION
    // =========================================================

    @Test
    void crossTenantProductCannotBeConfiguredByAdmin()
        throws Exception {

        UUID otherOrganizationId =
            insertOrganization(
                "OTHER"
            );

        UUID categoryId =
            insertCategory(
                otherOrganizationId,
                "Foreign Category",
                "foreign-category"
            );

        UUID foreignProductId =
            insertProduct(
                otherOrganizationId,
                categoryId,
                "FOREIGN-ADV",
                "Foreign Advanced Product",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/variants",
                    foreignProductId
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
                          "name": "Illegal Variant",
                          "sku": "ILLEGAL-V",
                          "priceDelta": 0,
                          "active": true,
                          "displayOrder": 0
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
    void configurationCannotCrossTenantBoundary()
        throws Exception {

        UUID otherOrganizationId =
            insertOrganization(
                "OTHER"
            );

        UUID categoryId =
            insertCategory(
                otherOrganizationId,
                "Foreign Read",
                "foreign-read"
            );

        UUID foreignProductId =
            insertProduct(
                otherOrganizationId,
                categoryId,
                "FOREIGN-READ",
                "Foreign Read Product",
                true
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/configuration",
                    foreignProductId
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
    // JWT HELPERS
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
                .audience(
                    List.of(
                        securityProperties
                            .audience()
                    )
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
    // DATABASE HELPERS
    // =========================================================

    private UUID insertOwnedProduct(
        String sku,
        String name
    ) {
        UUID categoryId =
            insertCategory(
                organizationId,
                "Category " + sku,
                "category-"
                    + UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
            );

        return insertProduct(
            organizationId,
            categoryId,
            sku,
            name,
            true
        );
    }

    private UUID insertOrganization(
        String prefix
    ) {
        UUID id =
            UUID.randomUUID();

        String suffix =
            UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);

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
            prefix + suffix
        );

        return id;
    }

    private UUID insertCategory(
        UUID tenantId,
        String name,
        String slug
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
            tenantId,
            name,
            slug
        );

        return id;
    }

    private UUID insertProduct(
        UUID tenantId,
        UUID categoryId,
        String sku,
        String name,
        boolean active
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
                'PACKAGED',
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
            sku,
            name,
            active
        );

        return id;
    }

    private UUID insertVariant(
        UUID productId,
        String name,
        String sku,
        boolean active,
        int displayOrder
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
            VALUES (?, ?, ?, ?, 0, ?, ?)
            """,
            id,
            productId,
            name,
            sku,
            active,
            displayOrder
        );

        return id;
    }

    private UUID insertBarcode(
        UUID productId,
        UUID variantId,
        String barcode,
        boolean primary,
        boolean active
    ) {
        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO product_barcodes (
                id,
                product_id,
                variant_id,
                barcode,
                pack_quantity,
                is_primary,
                is_active
            )
            VALUES (?, ?, ?, ?, 1, ?, ?)
            """,
            id,
            productId,
            variantId,
            barcode,
            primary,
            active
        );

        return id;
    }

    private UUID insertOptionGroup(
        UUID productId,
        String name,
        int minSelect,
        int maxSelect,
        boolean required,
        int displayOrder
    ) {
        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO product_option_groups (
                id,
                product_id,
                name,
                min_select,
                max_select,
                required,
                display_order
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            productId,
            name,
            minSelect,
            maxSelect,
            required,
            displayOrder
        );

        return id;
    }

    private UUID insertOption(
        UUID groupId,
        String name,
        boolean active,
        int displayOrder
    ) {
        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO product_options (
                id,
                option_group_id,
                name,
                price_delta,
                is_active,
                display_order
            )
            VALUES (?, ?, ?, 0, ?, ?)
            """,
            id,
            groupId,
            name,
            active,
            displayOrder
        );

        return id;
    }

    private String uniqueBarcode() {
        String digits =
            UUID.randomUUID()
                .toString()
                .replaceAll(
                    "[^0-9]",
                    ""
                );

        String suffix =
            String.format(
                "%012d",
                Math.abs(
                    UUID.randomUUID()
                        .getLeastSignificantBits()
                        % 1_000_000_000_000L
                )
            );

        return suffix;
    }
}