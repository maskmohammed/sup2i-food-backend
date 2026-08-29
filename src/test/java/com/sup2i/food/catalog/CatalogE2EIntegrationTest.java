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

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
class CatalogE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName("sup2i_food_test");

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

        organizationId = UUID.randomUUID();
        userId = UUID.randomUUID();

        email =
            "catalog-" + suffix + "@sup2i.test";

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
            "Catalog E2E " + suffix,
            "CAT" + suffix
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
            "Tester"
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "catalog-e2e",
                InetAddress.getLoopbackAddress()
            );

        Jwt sessionJwt =
            jwtDecoder.decode(
                tokens.accessToken()
            );

        sessionId =
            sessionJwt.getClaimAsString("sid");

        assertThat(sessionId)
            .isNotBlank();
    }

    // =========================================================
    // SECURITY
    // =========================================================

    @Test
    void catalogRequiresAuthentication()
        throws Exception {

        mockMvc.perform(
                get("/api/v1/catalog/categories")
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("UNAUTHORIZED")
            );
    }

    @Test
    void catalogReadRequiresPermission()
        throws Exception {

        mockMvc.perform(
                get("/api/v1/catalog/categories")
                    .header(
                        "Authorization",
                        bearer()
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
    void categoryWriteRequiresPermission()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/admin/categories")
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
                          "name": "Unauthorized Category",
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

    @Test
    void productWriteRequiresPermission()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Security Category",
                "security-category",
                true
            );

        mockMvc.perform(
                post("/api/v1/admin/products")
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        validProductJson(
                            categoryId,
                            "SEC-001",
                            "Security Product"
                        )
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
    // CATEGORY WRITE
    // =========================================================

    @Test
    void createCategoryNormalizesSlugAndPersistsTenant()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header(
                        "Authorization",
                        bearer("category.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "name": "Boissons Fraiches",
                          "displayOrder": 7,
                          "active": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isCreated()
            );

        String slug =
            jdbcTemplate.queryForObject(
                """
                SELECT slug
                FROM categories
                WHERE organization_id = ?
                  AND name = ?
                """,
                String.class,
                organizationId,
                "Boissons Fraiches"
            );

        Integer displayOrder =
            jdbcTemplate.queryForObject(
                """
                SELECT display_order
                FROM categories
                WHERE organization_id = ?
                  AND name = ?
                """,
                Integer.class,
                organizationId,
                "Boissons Fraiches"
            );

        assertThat(slug)
            .isEqualTo("boissons-fraiches");

        assertThat(displayOrder)
            .isEqualTo(7);
    }

    @Test
    void duplicateCategorySlugReturnsConflict()
        throws Exception {

        insertCategory(
            organizationId,
            "Existing",
            "boissons",
            true
        );

        mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header(
                        "Authorization",
                        bearer("category.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "name": "Another Category",
                          "slug": " BOISSONS ",
                          "displayOrder": 0,
                          "active": true
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
    void parentCategoryCannotComeFromAnotherTenant()
        throws Exception {

        UUID otherOrganizationId =
            insertOrganization("OTHER");

        UUID foreignParent =
            insertCategory(
                otherOrganizationId,
                "Foreign Parent",
                "foreign-parent",
                true
            );

        String body =
            """
            {
              "name": "Illegal Child",
              "slug": "illegal-child",
              "parentId": "%s",
              "displayOrder": 0,
              "active": true
            }
            """.formatted(foreignParent);

        mockMvc.perform(
                post("/api/v1/admin/categories")
                    .header(
                        "Authorization",
                        bearer("category.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(body)
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
    // PRODUCT WRITE
    // =========================================================

    @Test
    void createProductNormalizesSkuAndCreatesInitialPriceHistory()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Snacks",
                "snacks",
                true
            );

        mockMvc.perform(
                post("/api/v1/admin/products")
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        validProductJson(
                            categoryId,
                            " snack-001 ",
                            "Sandwich Poulet"
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            );

        UUID productId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM products
                WHERE organization_id = ?
                  AND sku = ?
                """,
                UUID.class,
                organizationId,
                "SNACK-001"
            );

        assertThat(productId)
            .isNotNull();

        UUID persistedOrganization =
            jdbcTemplate.queryForObject(
                """
                SELECT organization_id
                FROM products
                WHERE id = ?
                """,
                UUID.class,
                productId
            );

        BigDecimal price =
            jdbcTemplate.queryForObject(
                """
                SELECT base_price
                FROM products
                WHERE id = ?
                """,
                BigDecimal.class,
                productId
            );

        Long historyCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM product_price_history
                WHERE product_id = ?
                """,
                Long.class,
                productId
            );

        BigDecimal historicalPrice =
            jdbcTemplate.queryForObject(
                """
                SELECT price
                FROM product_price_history
                WHERE product_id = ?
                  AND effective_to IS NULL
                """,
                BigDecimal.class,
                productId
            );

        UUID changedBy =
            jdbcTemplate.queryForObject(
                """
                SELECT changed_by
                FROM product_price_history
                WHERE product_id = ?
                  AND effective_to IS NULL
                """,
                UUID.class,
                productId
            );

        String reason =
            jdbcTemplate.queryForObject(
                """
                SELECT reason
                FROM product_price_history
                WHERE product_id = ?
                  AND effective_to IS NULL
                """,
                String.class,
                productId
            );

        assertThat(persistedOrganization)
            .isEqualTo(organizationId);

        assertThat(price)
            .isEqualByComparingTo("25.50");

        assertThat(historyCount)
            .isEqualTo(1L);

        assertThat(historicalPrice)
            .isEqualByComparingTo("25.50");

        assertThat(changedBy)
            .isEqualTo(userId);

        assertThat(reason)
            .isEqualTo("INITIAL_PRODUCT_PRICE");
    }

    @Test
    void duplicateProductSkuReturnsConflict()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Duplicate Products",
                "duplicate-products",
                true
            );

        insertProduct(
            organizationId,
            categoryId,
            "DUP-001",
            "Existing Product",
            true
        );

        mockMvc.perform(
                post("/api/v1/admin/products")
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        validProductJson(
                            categoryId,
                            " dup-001 ",
                            "Duplicate Product"
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
    void productCategoryCannotComeFromAnotherTenant()
        throws Exception {

        UUID otherOrganizationId =
            insertOrganization("OTHER");

        UUID foreignCategoryId =
            insertCategory(
                otherOrganizationId,
                "Foreign Category",
                "foreign-category",
                true
            );

        mockMvc.perform(
                post("/api/v1/admin/products")
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        validProductJson(
                            foreignCategoryId,
                            "FOREIGN-001",
                            "Illegal Foreign Product"
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
    // CATALOG READ / MULTI-TENANT
    // =========================================================

    @Test
    void categoryCatalogReturnsOnlyActiveCurrentTenantCategories()
        throws Exception {

        insertCategory(
            organizationId,
            "Own Active",
            "own-active",
            true
        );

        insertCategory(
            organizationId,
            "Own Inactive",
            "own-inactive",
            false
        );

        UUID otherOrganizationId =
            insertOrganization("OTHER");

        insertCategory(
            otherOrganizationId,
            "Foreign Active",
            "foreign-active",
            true
        );

        String body =
            mockMvc.perform(
                    get("/api/v1/catalog/categories")
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
            .contains("own-active")
            .doesNotContain("own-inactive")
            .doesNotContain("foreign-active");
    }

    @Test
    void productCatalogReturnsOnlyActiveCurrentTenantProducts()
        throws Exception {

        UUID ownCategory =
            insertCategory(
                organizationId,
                "Own Products",
                "own-products",
                true
            );

        insertProduct(
            organizationId,
            ownCategory,
            "OWN-ACTIVE",
            "Own Active Product",
            true
        );

        insertProduct(
            organizationId,
            ownCategory,
            "OWN-INACTIVE",
            "Own Inactive Product",
            false
        );

        UUID otherOrganizationId =
            insertOrganization("OTHER");

        UUID foreignCategory =
            insertCategory(
                otherOrganizationId,
                "Foreign Products",
                "foreign-products",
                true
            );

        insertProduct(
            otherOrganizationId,
            foreignCategory,
            "FOREIGN-ACTIVE",
            "Foreign Active Product",
            true
        );

        String body =
            mockMvc.perform(
                    get("/api/v1/catalog/products")
                        .param("page", "0")
                        .param("size", "20")
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
            .contains("OWN-ACTIVE")
            .doesNotContain("OWN-INACTIVE")
            .doesNotContain("FOREIGN-ACTIVE");
    }

    @Test
    void productCatalogCanFilterByCategory()
        throws Exception {

        UUID categoryA =
            insertCategory(
                organizationId,
                "Category A",
                "category-a",
                true
            );

        UUID categoryB =
            insertCategory(
                organizationId,
                "Category B",
                "category-b",
                true
            );

        insertProduct(
            organizationId,
            categoryA,
            "FILTER-A",
            "Filter A",
            true
        );

        insertProduct(
            organizationId,
            categoryB,
            "FILTER-B",
            "Filter B",
            true
        );

        String body =
            mockMvc.perform(
                    get("/api/v1/catalog/products")
                        .param(
                            "categoryId",
                            categoryA.toString()
                        )
                        .param("page", "0")
                        .param("size", "20")
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
            .contains("FILTER-A")
            .doesNotContain("FILTER-B");
    }

    @Test
    void productByIdCannotCrossTenantBoundary()
        throws Exception {

        UUID otherOrganizationId =
            insertOrganization("OTHER");

        UUID foreignCategory =
            insertCategory(
                otherOrganizationId,
                "Foreign Detail",
                "foreign-detail",
                true
            );

        UUID foreignProduct =
            insertProduct(
                otherOrganizationId,
                foreignCategory,
                "FOREIGN-DETAIL",
                "Foreign Detail Product",
                true
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{id}",
                    foreignProduct
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

    @Test
    void inactiveProductReturnsProductUnavailable()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Inactive Product Category",
                "inactive-product-category",
                true
            );

        UUID productId =
            insertProduct(
                organizationId,
                categoryId,
                "UNAVAILABLE-001",
                "Unavailable Product",
                false
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{id}",
                    productId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
            )
            .andExpect(
                status().is(422)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PRODUCT_UNAVAILABLE")
            );
    }

    @Test
    void categoryListReturnsPageResponseAndHonorsSize()
        throws Exception {

        insertCategory(
            organizationId,
            "Hardening Category A",
            "hardening-category-a",
            true
        );

        insertCategory(
            organizationId,
            "Hardening Category B",
            "hardening-category-b",
            true
        );

        Long expectedTotal =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM categories
                WHERE organization_id = ?
                  AND is_active = TRUE
                """,
                Long.class,
                organizationId
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/categories"
                )
                    .queryParam(
                        "page",
                        "0"
                    )
                    .queryParam(
                        "size",
                        "1"
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
                jsonPath("$.content.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.page")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.size")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.totalElements")
                    .value(
                        expectedTotal.intValue()
                    )
            );
    }

    @Test
    void catalogErrorPropagatesRequestId()
        throws Exception {

        String requestId =
            "catalog-hardening-"
                + UUID.randomUUID();

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{id}",
                    UUID.randomUUID()
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
                    .header(
                        "X-Request-ID",
                        requestId
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .header()
                    .string(
                        "X-Request-ID",
                        requestId
                    )
            )
            .andExpect(
                jsonPath("$.traceId")
                    .value(requestId)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );
    }
    @Test
    void punctuationOnlyCategorySlugReturnsValidationError()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/admin/categories"
                )
                    .header(
                        "Authorization",
                        bearer("category.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "name": "Invalid Slug",
                          "slug": " !!! --- ___ ... ",
                          "displayOrder": 0,
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
    void productNumericDatabaseLimitsAreAccepted()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Numeric Limits",
                "numeric-limits",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products"
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
                          "categoryId": "%s",
                          "sku": "NUMERIC-LIMIT",
                          "name": "Numeric Limit Product",
                          "productType": "PACKAGED",
                          "basePrice": 9999999999.99,
                          "taxRate": 100.00,
                          "active": true
                        }
                        """.formatted(
                            categoryId
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            );

        BigDecimal storedPrice =
            jdbcTemplate.queryForObject(
                """
                SELECT base_price
                FROM products
                WHERE organization_id = ?
                  AND sku = 'NUMERIC-LIMIT'
                """,
                BigDecimal.class,
                organizationId
            );

        assertThat(storedPrice)
            .isEqualByComparingTo(
                "9999999999.99"
            );
    }

    @Test
    void productBasePricePrecisionOverflowReturnsValidationError()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Price Overflow",
                "price-overflow",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products"
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
                          "categoryId": "%s",
                          "sku": "PRICE-OVERFLOW",
                          "name": "Price Overflow Product",
                          "productType": "PACKAGED",
                          "basePrice": 10000000000.00,
                          "taxRate": 0,
                          "active": true
                        }
                        """.formatted(
                            categoryId
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
    void productTaxRateScaleOverflowReturnsValidationError()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Tax Scale",
                "tax-scale",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products"
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
                          "categoryId": "%s",
                          "sku": "TAX-SCALE",
                          "name": "Tax Scale Product",
                          "productType": "PACKAGED",
                          "basePrice": 10.00,
                          "taxRate": 10.001,
                          "active": true
                        }
                        """.formatted(
                            categoryId
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
    // =========================================================
    // VALIDATION / BAD JSON
    // =========================================================

    @Test
    void invalidProductBusinessFieldsReturnValidationError()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Validation",
                "validation",
                true
            );

        String body =
            """
            {
              "categoryId": "%s",
              "sku": "INVALID-PRICE",
              "name": "Invalid Price",
              "productType": "PACKAGED",
              "basePrice": -1,
              "taxRate": 101,
              "preparationMinutes": -4,
              "trackStock": true,
              "prepared": false,
              "active": true
            }
            """.formatted(categoryId);

        mockMvc.perform(
                post("/api/v1/admin/products")
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(body)
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
    void unknownProductTypeReturnsValidationError()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Enum Validation",
                "enum-validation",
                true
            );

        String body =
            """
            {
              "categoryId": "%s",
              "sku": "UNKNOWN-TYPE",
              "name": "Unknown Type",
              "productType": "NOT_A_REAL_TYPE",
              "basePrice": 10.00,
              "taxRate": 0,
              "preparationMinutes": 0,
              "trackStock": true,
              "prepared": false,
              "active": true
            }
            """.formatted(categoryId);

        mockMvc.perform(
                post("/api/v1/admin/products")
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(body)
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
    // JWT WITH REAL ACTIVE SERVER SESSION
    // =========================================================

    private String bearer(
        String... permissions
    ) {
        return "Bearer " + token(permissions);
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
        String slug,
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
            slug,
            active
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

    private String validProductJson(
        UUID categoryId,
        String sku,
        String name
    ) {

        return """
            {
              "categoryId": "%s",
              "sku": "%s",
              "name": "%s",
              "description": "Produit test catalogue",
              "imageUrl": "https://example.test/product.png",
              "productType": "PREPARED",
              "basePrice": 25.50,
              "taxRate": 0,
              "preparationMinutes": 8,
              "trackStock": true,
              "prepared": true,
              "active": true
            }
            """.formatted(
                categoryId,
                sku,
                name
            );
    }
}