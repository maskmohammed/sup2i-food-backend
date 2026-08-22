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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class CatalogRecipeE2EIntegrationTest {

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
            "catalog-recipe-"
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
            "Catalog Recipe " + suffix,
            "REC" + suffix
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
            "Recipe"
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "catalog-recipe-e2e",
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
    void ingredientWriteRequiresProductWritePermission()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/admin/ingredients"
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
                          "code": "MILK",
                          "name": "Milk",
                          "baseUnit": "LITER"
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
    void recipeWriteRequiresProductWritePermission()
        throws Exception {

        UUID productId =
            insertOwnedPreparedProduct(
                "RBAC-RECIPE"
            );

        UUID ingredientId =
            insertOwnedIngredient(
                "RBAC-ING",
                "GRAM",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/recipes",
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
                        recipeBody(
                            ingredientId
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

    @Test
    void optionComponentWriteRequiresProductWritePermission()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "RBAC-OPTION"
            );

        UUID optionId =
            insertOption(
                productId,
                "RBAC Option"
            );

        UUID componentProduct =
            insertOwnedPackagedProduct(
                "RBAC-COMP"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components",
                    productId,
                    optionId
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
                          "componentProductId": "%s"
                        }
                        """.formatted(
                            componentProduct
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
    // INGREDIENTS
    // =========================================================

    @Test
    void createIngredientNormalizesCodeAndDefaultsActive()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/admin/ingredients"
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
                          "code": "  milk  ",
                          "name": "  Fresh Milk  ",
                          "baseUnit": "LITER"
                        }
                        """
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("MILK")
            )
            .andExpect(
                jsonPath("$.name")
                    .value("Fresh Milk")
            )
            .andExpect(
                jsonPath("$.baseUnit")
                    .value("LITER")
            )
            .andExpect(
                jsonPath("$.active")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.allergens.length()")
                    .value(0)
            );

        String code =
            jdbcTemplate.queryForObject(
                """
                SELECT code
                FROM ingredients
                WHERE organization_id = ?
                  AND code = 'MILK'
                """,
                String.class,
                organizationId
            );

        assertThat(code)
            .isEqualTo("MILK");
    }

    @Test
    void duplicateIngredientCodeReturnsConflict()
        throws Exception {

        postIngredient(
            """
            {
              "code": "SUGAR",
              "name": "Sugar",
              "baseUnit": "GRAM"
            }
            """
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/ingredients"
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
                          "code": " sugar ",
                          "name": "Second Sugar",
                          "baseUnit": "GRAM"
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
    void ingredientListIsTenantScopedAndHidesInactive()
        throws Exception {

        UUID activeIngredient =
            insertOwnedIngredient(
                "VISIBLE",
                "GRAM",
                true
            );

        insertOwnedIngredient(
            "HIDDEN",
            "GRAM",
            false
        );

        UUID otherOrganization =
            insertOrganization(
                "ING-OTHER"
            );

        insertIngredient(
            otherOrganization,
            "FOREIGN",
            "Foreign Ingredient",
            "GRAM",
            true
        );

        String body =
            mockMvc.perform(
                    get(
                        "/api/v1/admin/ingredients"
                    )
                        .header(
                            "Authorization",
                            bearer("product.write")
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
            .contains(
                activeIngredient.toString()
            )
            .contains("VISIBLE")
            .doesNotContain("HIDDEN")
            .doesNotContain("Foreign Ingredient");
    }

    @Test
    void ingredientLookupCannotCrossTenantBoundary()
        throws Exception {

        UUID otherOrganization =
            insertOrganization(
                "ING-LOOKUP-OTHER"
            );

        UUID foreignIngredient =
            insertIngredient(
                otherOrganization,
                "FOREIGN-LOOKUP",
                "Foreign Lookup Ingredient",
                "GRAM",
                true
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/ingredients/{ingredientId}",
                    foreignIngredient
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
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
    void replaceIngredientAllergensPersistsLinks()
        throws Exception {

        UUID ingredientId =
            insertOwnedIngredient(
                "ALLERGEN-ING",
                "GRAM",
                true
            );

        UUID gluten =
            insertAllergen(
                organizationId,
                "GLUTEN",
                "Gluten",
                true
            );

        UUID milk =
            insertAllergen(
                organizationId,
                "MILK-A",
                "Milk",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/ingredients/{ingredientId}/allergens",
                    ingredientId
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
                          "allergenIds": [
                            "%s",
                            "%s"
                          ]
                        }
                        """.formatted(
                            gluten,
                            milk
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.allergens.length()")
                    .value(2)
            );

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ingredient_allergens
                WHERE ingredient_id = ?
                """,
                Long.class,
                ingredientId
            );

        assertThat(count)
            .isEqualTo(2L);
    }

    @Test
    void foreignIngredientAllergenIsRejected()
        throws Exception {

        UUID ingredientId =
            insertOwnedIngredient(
                "FOREIGN-ALLERGEN-ING",
                "GRAM",
                true
            );

        UUID otherOrganization =
            insertOrganization(
                "ALLERGEN-OTHER"
            );

        UUID foreignAllergen =
            insertAllergen(
                otherOrganization,
                "FOREIGN-A",
                "Foreign Allergen",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/ingredients/{ingredientId}/allergens",
                    ingredientId
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
                          "allergenIds": [
                            "%s"
                          ]
                        }
                        """.formatted(
                            foreignAllergen
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
    void duplicateIngredientAllergenReferencesReturnValidationError()
        throws Exception {

        UUID ingredientId =
            insertOwnedIngredient(
                "DUP-ALLERGEN-ING",
                "GRAM",
                true
            );

        UUID allergenId =
            insertAllergen(
                organizationId,
                "DUP-A",
                "Duplicate Allergen",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/ingredients/{ingredientId}/allergens",
                    ingredientId
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
                          "allergenIds": [
                            "%s",
                            "%s"
                          ]
                        }
                        """.formatted(
                            allergenId,
                            allergenId
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
    void emptyIngredientAllergenListClearsLinks()
        throws Exception {

        UUID ingredientId =
            insertOwnedIngredient(
                "CLEAR-ALLERGEN-ING",
                "GRAM",
                true
            );

        UUID allergenId =
            insertAllergen(
                organizationId,
                "CLEAR-A",
                "Clear Allergen",
                true
            );

        jdbcTemplate.update(
            """
            INSERT INTO ingredient_allergens (
                ingredient_id,
                allergen_id
            )
            VALUES (?, ?)
            """,
            ingredientId,
            allergenId
        );

        mockMvc.perform(
                put(
                    "/api/v1/admin/ingredients/{ingredientId}/allergens",
                    ingredientId
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
                          "allergenIds": []
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.allergens.length()")
                    .value(0)
            );

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ingredient_allergens
                WHERE ingredient_id = ?
                """,
                Long.class,
                ingredientId
            );

        assertThat(count)
            .isZero();
    }

    // =========================================================
    // INGREDIENT LIFECYCLE
    // =========================================================

    @Test
    void updateIngredientNormalizesAndDeactivates()
        throws Exception {

        UUID ingredientId =
            insertOwnedIngredient(
                "UPDATE-ING",
                "GRAM",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/ingredients/{ingredientId}",
                    ingredientId
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
                          "code": "  fresh-code  ",
                          "name": "  Renamed Ingredient  ",
                          "baseUnit": "KILOGRAM",
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
                    .value("FRESH-CODE")
            )
            .andExpect(
                jsonPath("$.name")
                    .value("Renamed Ingredient")
            )
            .andExpect(
                jsonPath("$.baseUnit")
                    .value("KILOGRAM")
            )
            .andExpect(
                jsonPath("$.active")
                    .value(false)
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/ingredients/{ingredientId}",
                    ingredientId
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
                jsonPath("$.active")
                    .value(false)
            );

        String listBody =
            mockMvc.perform(
                    get(
                        "/api/v1/admin/ingredients"
                    )
                        .header(
                            "Authorization",
                            bearer("product.write")
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(listBody)
            .doesNotContain(
                ingredientId.toString()
            );
    }

    @Test
    void updateIngredientDuplicateCodeReturnsConflict()
        throws Exception {

        UUID first =
            insertOwnedIngredient(
                "UPDATE-DUP-A",
                "GRAM",
                true
            );

        UUID second =
            insertOwnedIngredient(
                "UPDATE-DUP-B",
                "GRAM",
                true
            );

        jdbcTemplate.update(
            """
            UPDATE ingredients
            SET code = UPPER(code)
            WHERE id = ?
            """,
            first
        );

        String firstCode =
            jdbcTemplate.queryForObject(
                """
                SELECT code
                FROM ingredients
                WHERE id = ?
                """,
                String.class,
                first
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/ingredients/{ingredientId}",
                    second
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
                          "code": "%s",
                          "name": "Duplicate Code",
                          "baseUnit": "GRAM",
                          "active": true
                        }
                        """.formatted(
                            firstCode
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
    void updateIngredientCannotCrossTenantBoundary()
        throws Exception {

        UUID otherOrganization =
            insertOrganization(
                "UPDATE-FOREIGN"
            );

        UUID foreignIngredient =
            insertIngredient(
                otherOrganization,
                "FOREIGN-UPDATE",
                "Foreign Update",
                "GRAM",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/ingredients/{ingredientId}",
                    foreignIngredient
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
                          "code": "FOREIGN",
                          "name": "Foreign",
                          "baseUnit": "GRAM",
                          "active": false
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
    void updateIngredientRequiresProductWritePermission()
        throws Exception {

        UUID ingredientId =
            insertOwnedIngredient(
                "UPDATE-RBAC",
                "GRAM",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/ingredients/{ingredientId}",
                    ingredientId
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
                          "code": "UPDATE-RBAC",
                          "name": "RBAC",
                          "baseUnit": "GRAM",
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
    // RECIPES / BOM
    // =========================================================

    @Test
    void createFirstRecipeVersionPersistsBomAndDefaults()
        throws Exception {

        UUID productId =
            insertOwnedPreparedProduct(
                "RECIPE-V1"
            );

        UUID flour =
            insertOwnedIngredient(
                "FLOUR",
                "GRAM",
                true
            );

        UUID oil =
            insertOwnedIngredient(
                "OIL",
                "MILLILITER",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/recipes",
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
                          "items": [
                            {
                              "ingredientId": "%s",
                              "quantity": 200.000,
                              "unit": "GRAM",
                              "wasteFactor": 0.0500
                            },
                            {
                              "ingredientId": "%s",
                              "quantity": 10.000,
                              "unit": "MILLILITER",
                              "critical": false
                            }
                          ]
                        }
                        """.formatted(
                            flour,
                            oil
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.productId")
                    .value(productId.toString())
            )
            .andExpect(
                jsonPath("$.variantId")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.version")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.active")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.items.length()")
                    .value(2)
            );

        Integer version =
            jdbcTemplate.queryForObject(
                """
                SELECT version
                FROM recipes
                WHERE product_id = ?
                  AND variant_id IS NULL
                  AND is_active = TRUE
                  AND effective_to IS NULL
                """,
                Integer.class,
                productId
            );

        Long itemCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM recipe_items ri
                JOIN recipes r
                  ON r.id = ri.recipe_id
                WHERE r.product_id = ?
                  AND r.version = 1
                """,
                Long.class,
                productId
            );

        assertThat(version)
            .isEqualTo(1);

        assertThat(itemCount)
            .isEqualTo(2L);
    }

    @Test
    void secondRecipeVersionClosesFirstAndIncrements()
        throws Exception {

        UUID productId =
            insertOwnedPreparedProduct(
                "RECIPE-VERSION"
            );

        UUID ingredientId =
            insertOwnedIngredient(
                "VERSION-ING",
                "GRAM",
                true
            );

        postRecipe(
            productId,
            recipeBody(
                ingredientId
            )
        );

        UUID versionOneId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM recipes
                WHERE product_id = ?
                  AND variant_id IS NULL
                  AND version = 1
                """,
                UUID.class,
                productId
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/recipes",
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
                          "items": [
                            {
                              "ingredientId": "%s",
                              "quantity": 2.000,
                              "unit": "GRAM"
                            }
                          ]
                        }
                        """.formatted(
                            ingredientId
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.version")
                    .value(2)
            );

        Boolean firstClosed =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    is_active = FALSE
                    AND effective_to IS NOT NULL
                FROM recipes
                WHERE id = ?
                """,
                Boolean.class,
                versionOneId
            );

        Long openRecipes =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM recipes
                WHERE product_id = ?
                  AND variant_id IS NULL
                  AND is_active = TRUE
                  AND effective_to IS NULL
                """,
                Long.class,
                productId
            );

        assertThat(firstClosed)
            .isTrue();

        assertThat(openRecipes)
            .isEqualTo(1L);

        mockMvc.perform(
                get(
                    "/api/v1/admin/products/{productId}/recipes/current",
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
                jsonPath("$.version")
                    .value(2)
            );
    }

    @Test
    void productAndVariantRecipesVersionIndependently()
        throws Exception {

        UUID productId =
            insertOwnedPreparedProduct(
                "RECIPE-VARIANTS"
            );

        UUID variantId =
            insertVariant(
                productId,
                "Large",
                true
            );

        UUID baseIngredient =
            insertOwnedIngredient(
                "BASE-ING",
                "GRAM",
                true
            );

        UUID variantIngredient =
            insertOwnedIngredient(
                "VAR-ING",
                "GRAM",
                true
            );

        postRecipe(
            productId,
            recipeBody(
                baseIngredient
            )
        );

        postRecipe(
            productId,
            """
            {
              "variantId": "%s",
              "items": [
                {
                  "ingredientId": "%s",
                  "quantity": 1.000,
                  "unit": "GRAM"
                }
              ]
            }
            """.formatted(
                variantId,
                variantIngredient
            )
        );

        mockMvc.perform(
                get(
                    "/api/v1/admin/products/{productId}/recipes/current",
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
                jsonPath("$.version")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.variantId")
                    .doesNotExist()
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/products/{productId}/recipes/current",
                    productId
                )
                    .queryParam(
                        "variantId",
                        variantId.toString()
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
                jsonPath("$.version")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.variantId")
                    .value(variantId.toString())
            );

        Long activeCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM recipes
                WHERE product_id = ?
                  AND is_active = TRUE
                  AND effective_to IS NULL
                """,
                Long.class,
                productId
            );

        assertThat(activeCount)
            .isEqualTo(2L);
    }

    @Test
    void recipeVariantMustBelongToProduct()
        throws Exception {

        UUID productA =
            insertOwnedPreparedProduct(
                "RECIPE-A"
            );

        UUID productB =
            insertOwnedPreparedProduct(
                "RECIPE-B"
            );

        UUID variantB =
            insertVariant(
                productB,
                "Wrong Variant",
                true
            );

        UUID ingredient =
            insertOwnedIngredient(
                "WRONG-VAR-ING",
                "GRAM",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/recipes",
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
                          "items": [
                            {
                              "ingredientId": "%s",
                              "quantity": 1,
                              "unit": "GRAM"
                            }
                          ]
                        }
                        """.formatted(
                            variantB,
                            ingredient
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
    void foreignRecipeIngredientIsRejected()
        throws Exception {

        UUID productId =
            insertOwnedPreparedProduct(
                "FOREIGN-RECIPE-ING"
            );

        UUID otherOrganization =
            insertOrganization(
                "FOREIGN-ING-ORG"
            );

        UUID foreignIngredient =
            insertIngredient(
                otherOrganization,
                "FOREIGN-RECIPE",
                "Foreign Recipe Ingredient",
                "GRAM",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/recipes",
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
                        recipeBody(
                            foreignIngredient
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
    void duplicateRecipeIngredientReferenceReturnsValidationError()
        throws Exception {

        UUID productId =
            insertOwnedPreparedProduct(
                "DUP-RECIPE-ING"
            );

        UUID ingredient =
            insertOwnedIngredient(
                "DUP-RECIPE",
                "GRAM",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/recipes",
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
                          "items": [
                            {
                              "ingredientId": "%s",
                              "quantity": 1,
                              "unit": "GRAM"
                            },
                            {
                              "ingredientId": "%s",
                              "quantity": 2,
                              "unit": "GRAM"
                            }
                          ]
                        }
                        """.formatted(
                            ingredient,
                            ingredient
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
    void invalidRecipeQuantityReturnsValidationError()
        throws Exception {

        UUID productId =
            insertOwnedPreparedProduct(
                "BAD-RECIPE-QTY"
            );

        UUID ingredient =
            insertOwnedIngredient(
                "BAD-QTY",
                "GRAM",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/recipes",
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
                          "items": [
                            {
                              "ingredientId": "%s",
                              "quantity": 0,
                              "unit": "GRAM"
                            }
                          ]
                        }
                        """.formatted(
                            ingredient
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
    void invalidRecipeWasteFactorReturnsValidationError()
        throws Exception {

        UUID productId =
            insertOwnedPreparedProduct(
                "BAD-WASTE"
            );

        UUID ingredient =
            insertOwnedIngredient(
                "BAD-WASTE-ING",
                "GRAM",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/recipes",
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
                          "items": [
                            {
                              "ingredientId": "%s",
                              "quantity": 1,
                              "unit": "GRAM",
                              "wasteFactor": 1.0000
                            }
                          ]
                        }
                        """.formatted(
                            ingredient
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
    void databaseRejectsRecipeVariantProductMismatch() {

        UUID productA =
            insertOwnedPreparedProduct(
                "DB-RECIPE-A"
            );

        UUID productB =
            insertOwnedPreparedProduct(
                "DB-RECIPE-B"
            );

        UUID variantB =
            insertVariant(
                productB,
                "DB Wrong Variant",
                true
            );

        assertThatThrownBy(() ->
            jdbcTemplate.update(
                """
                INSERT INTO recipes (
                    id,
                    product_id,
                    variant_id,
                    version,
                    is_active,
                    effective_from
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    99,
                    FALSE,
                    CURRENT_TIMESTAMP
                )
                """,
                UUID.randomUUID(),
                productA,
                variantB
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    // =========================================================
    // PRODUCT OPTION COMPONENTS
    // =========================================================

    @Test
    void optionComponentRequiresExactlyOneSubject()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "COMP-SUBJECT"
            );

        UUID optionId =
            insertOption(
                productId,
                "Subject Option"
            );

        UUID componentProduct =
            insertOwnedPackagedProduct(
                "COMP-SUBJECT-P"
            );

        UUID ingredient =
            insertOwnedIngredient(
                "COMP-SUBJECT-I",
                "GRAM",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components",
                    productId,
                    optionId
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
                        {}
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

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components",
                    productId,
                    optionId
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
                          "componentProductId": "%s",
                          "ingredientId": "%s"
                        }
                        """.formatted(
                            componentProduct,
                            ingredient
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
    void productComponentDefaultsQuantityAndUnit()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "COMP-PRODUCT"
            );

        UUID optionId =
            insertOption(
                productId,
                "Product Component Option"
            );

        UUID componentProduct =
            insertOwnedPackagedProduct(
                "COMPONENT-P"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components",
                    productId,
                    optionId
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
                          "componentProductId": "%s"
                        }
                        """.formatted(
                            componentProduct
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.componentProductId")
                    .value(
                        componentProduct.toString()
                    )
            )
            .andExpect(
                jsonPath("$.quantity")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.unit")
                    .value("PIECE")
            );
    }

    @Test
    void variantComponentPersists()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "COMP-VARIANT"
            );

        UUID optionId =
            insertOption(
                productId,
                "Variant Component Option"
            );

        UUID componentProduct =
            insertOwnedPackagedProduct(
                "VARIANT-SOURCE"
            );

        UUID variantId =
            insertVariant(
                componentProduct,
                "Large",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components",
                    productId,
                    optionId
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
                          "componentVariantId": "%s",
                          "quantity": 2.000,
                          "unit": "PIECE"
                        }
                        """.formatted(
                            variantId
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.componentVariantId")
                    .value(variantId.toString())
            )
            .andExpect(
                jsonPath("$.componentVariantName")
                    .value("Large")
            );
    }

    @Test
    void ingredientComponentPersists()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "COMP-INGREDIENT"
            );

        UUID optionId =
            insertOption(
                productId,
                "Ingredient Component Option"
            );

        UUID ingredientId =
            insertOwnedIngredient(
                "COMPONENT-ING",
                "GRAM",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components",
                    productId,
                    optionId
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
                          "ingredientId": "%s",
                          "quantity": 0.250,
                          "unit": "GRAM"
                        }
                        """.formatted(
                            ingredientId
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.ingredientId")
                    .value(ingredientId.toString())
            )
            .andExpect(
                jsonPath("$.quantity")
                    .value(0.250)
            )
            .andExpect(
                jsonPath("$.unit")
                    .value("GRAM")
            );
    }

    @Test
    void optionMustBelongToSelectedProduct()
        throws Exception {

        UUID productA =
            insertOwnedPackagedProduct(
                "OPTION-A"
            );

        UUID productB =
            insertOwnedPackagedProduct(
                "OPTION-B"
            );

        UUID optionB =
            insertOption(
                productB,
                "Option B"
            );

        UUID component =
            insertOwnedPackagedProduct(
                "OPTION-COMP"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components",
                    productA,
                    optionB
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
                          "componentProductId": "%s"
                        }
                        """.formatted(
                            component
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
    void foreignComponentProductIsRejected()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "FOREIGN-COMP-P-HOST"
            );

        UUID optionId =
            insertOption(
                productId,
                "Foreign Product Option"
            );

        UUID otherOrganization =
            insertOrganization(
                "FOREIGN-COMP-P"
            );

        UUID foreignCategory =
            insertCategory(
                otherOrganization,
                "Foreign Component Product Category"
            );

        UUID foreignProduct =
            insertProduct(
                otherOrganization,
                foreignCategory,
                "FOREIGN-COMP-P",
                "Foreign Component Product",
                "PACKAGED",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components",
                    productId,
                    optionId
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
                          "componentProductId": "%s"
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
    void foreignComponentVariantIsRejected()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "FOREIGN-COMP-V-HOST"
            );

        UUID optionId =
            insertOption(
                productId,
                "Foreign Variant Option"
            );

        UUID otherOrganization =
            insertOrganization(
                "FOREIGN-COMP-V"
            );

        UUID category =
            insertCategory(
                otherOrganization,
                "Foreign Variant Category"
            );

        UUID foreignProduct =
            insertProduct(
                otherOrganization,
                category,
                "FOREIGN-V-P",
                "Foreign Variant Product",
                "PACKAGED",
                true
            );

        UUID foreignVariant =
            insertVariant(
                foreignProduct,
                "Foreign Variant",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components",
                    productId,
                    optionId
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
                          "componentVariantId": "%s"
                        }
                        """.formatted(
                            foreignVariant
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
    void foreignComponentIngredientIsRejected()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "FOREIGN-COMP-I-HOST"
            );

        UUID optionId =
            insertOption(
                productId,
                "Foreign Ingredient Option"
            );

        UUID otherOrganization =
            insertOrganization(
                "FOREIGN-COMP-I"
            );

        UUID foreignIngredient =
            insertIngredient(
                otherOrganization,
                "FOREIGN-COMP-I",
                "Foreign Component Ingredient",
                "GRAM",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components",
                    productId,
                    optionId
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
                          "ingredientId": "%s",
                          "unit": "GRAM"
                        }
                        """.formatted(
                            foreignIngredient
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
    void databaseRejectsMultipleOptionComponentSubjects() {

        UUID productId =
            insertOwnedPackagedProduct(
                "DB-COMP-HOST"
            );

        UUID optionId =
            insertOption(
                productId,
                "DB Component Option"
            );

        UUID componentProduct =
            insertOwnedPackagedProduct(
                "DB-COMP-P"
            );

        UUID ingredientId =
            insertOwnedIngredient(
                "DB-COMP-I",
                "GRAM",
                true
            );

        assertThatThrownBy(() ->
            jdbcTemplate.update(
                """
                INSERT INTO product_option_components (
                    id,
                    product_option_id,
                    component_product_id,
                    ingredient_id,
                    quantity,
                    unit
                )
                VALUES (?, ?, ?, ?, 1, 'PIECE')
                """,
                UUID.randomUUID(),
                optionId,
                componentProduct,
                ingredientId
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void componentListReturnsAllCreatedSubjects()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "COMP-LIST"
            );

        UUID optionId =
            insertOption(
                productId,
                "List Option"
            );

        UUID productComponent =
            insertOwnedPackagedProduct(
                "LIST-P"
            );

        UUID variantSource =
            insertOwnedPackagedProduct(
                "LIST-V-SOURCE"
            );

        UUID variantComponent =
            insertVariant(
                variantSource,
                "List Variant",
                true
            );

        UUID ingredientComponent =
            insertOwnedIngredient(
                "LIST-I",
                "GRAM",
                true
            );

        postComponent(
            productId,
            optionId,
            """
            {
              "componentProductId": "%s"
            }
            """.formatted(
                productComponent
            )
        );

        postComponent(
            productId,
            optionId,
            """
            {
              "componentVariantId": "%s"
            }
            """.formatted(
                variantComponent
            )
        );

        postComponent(
            productId,
            optionId,
            """
            {
              "ingredientId": "%s",
              "quantity": 5,
              "unit": "GRAM"
            }
            """.formatted(
                ingredientComponent
            )
        );

        String body =
            mockMvc.perform(
                    get(
                        "/api/v1/admin/products/{productId}/options/{optionId}/components",
                        productId,
                        optionId
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
                        .value(3)
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
            .contains(
                productComponent.toString()
            )
            .contains(
                variantComponent.toString()
            )
            .contains(
                ingredientComponent.toString()
            );
    }

    // =========================================================
    // OPTION COMPONENT LIFECYCLE
    // =========================================================

    @Test
    void deleteOptionComponentRemovesRow()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "DELETE-COMP-HOST"
            );

        UUID optionId =
            insertOption(
                productId,
                "Delete Component"
            );

        UUID componentProduct =
            insertOwnedPackagedProduct(
                "DELETE-COMP-P"
            );

        postComponent(
            productId,
            optionId,
            """
            {
              "componentProductId": "%s"
            }
            """.formatted(
                componentProduct
            )
        );

        UUID componentId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_option_components
                WHERE product_option_id = ?
                """,
                UUID.class,
                optionId
            );

        mockMvc.perform(
                delete(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components/{componentId}",
                    productId,
                    optionId,
                    componentId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
            )
            .andExpect(
                status().isNoContent()
            );

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM product_option_components
                WHERE id = ?
                """,
                Long.class,
                componentId
            );

        assertThat(count)
            .isZero();
    }

    @Test
    void deleteOptionComponentMustBelongToSelectedOption()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "DELETE-OPTION-HOST"
            );

        UUID optionA =
            insertOption(
                productId,
                "Delete Option A"
            );

        UUID optionB =
            insertOption(
                productId,
                "Delete Option B"
            );

        UUID componentProduct =
            insertOwnedPackagedProduct(
                "DELETE-OPTION-COMP"
            );

        postComponent(
            productId,
            optionB,
            """
            {
              "componentProductId": "%s"
            }
            """.formatted(
                componentProduct
            )
        );

        UUID componentId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_option_components
                WHERE product_option_id = ?
                """,
                UUID.class,
                optionB
            );

        mockMvc.perform(
                delete(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components/{componentId}",
                    productId,
                    optionA,
                    componentId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM product_option_components
                WHERE id = ?
                """,
                Long.class,
                componentId
            );

        assertThat(count)
            .isEqualTo(1L);
    }

    @Test
    void deleteOptionComponentRequiresProductWritePermission()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "DELETE-RBAC-HOST"
            );

        UUID optionId =
            insertOption(
                productId,
                "Delete RBAC"
            );

        UUID componentProduct =
            insertOwnedPackagedProduct(
                "DELETE-RBAC-COMP"
            );

        postComponent(
            productId,
            optionId,
            """
            {
              "componentProductId": "%s"
            }
            """.formatted(
                componentProduct
            )
        );

        UUID componentId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_option_components
                WHERE product_option_id = ?
                """,
                UUID.class,
                optionId
            );

        mockMvc.perform(
                delete(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components/{componentId}",
                    productId,
                    optionId,
                    componentId
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
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
    void deleteOptionComponentCannotCrossTenantBoundary()
        throws Exception {

        UUID productId =
            insertOwnedPackagedProduct(
                "DELETE-TENANT-HOST"
            );

        UUID optionId =
            insertOption(
                productId,
                "Delete Tenant"
            );

        UUID componentProduct =
            insertOwnedPackagedProduct(
                "DELETE-TENANT-COMP"
            );

        postComponent(
            productId,
            optionId,
            """
            {
              "componentProductId": "%s"
            }
            """.formatted(
                componentProduct
            )
        );

        UUID componentId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM product_option_components
                WHERE product_option_id = ?
                """,
                UUID.class,
                optionId
            );

        UUID otherOrganization =
            insertOrganization(
                "DELETE-OTHER"
            );

        UUID otherCategory =
            insertCategory(
                otherOrganization,
                "Delete Other Category"
            );

        UUID foreignProduct =
            insertProduct(
                otherOrganization,
                otherCategory,
                "DELETE-FOREIGN",
                "Delete Foreign Product",
                "PACKAGED",
                true
            );

        mockMvc.perform(
                delete(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components/{componentId}",
                    foreignProduct,
                    optionId,
                    componentId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
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
    // HTTP HELPERS
    // =========================================================

    private void postIngredient(
        String body
    ) throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/admin/ingredients"
                )
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
                status().isCreated()
            );
    }

    private void postRecipe(
        UUID productId,
        String body
    ) throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/recipes",
                    productId
                )
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
                status().isCreated()
            );
    }

    private void postComponent(
        UUID productId,
        UUID optionId,
        String body
    ) throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/admin/products/{productId}/options/{optionId}/components",
                    productId,
                    optionId
                )
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
                status().isCreated()
            );
    }

    private String recipeBody(
        UUID ingredientId
    ) {

        return """
            {
              "items": [
                {
                  "ingredientId": "%s",
                  "quantity": 1.000,
                  "unit": "GRAM"
                }
              ]
            }
            """.formatted(
                ingredientId
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

    private UUID insertOwnedPreparedProduct(
        String prefix
    ) {

        UUID categoryId =
            insertCategory(
                organizationId,
                prefix + " Category"
            );

        return insertProduct(
            organizationId,
            categoryId,
            prefix,
            prefix + " Product",
            "PREPARED",
            true
        );
    }

    private UUID insertOwnedPackagedProduct(
        String prefix
    ) {

        UUID categoryId =
            insertCategory(
                organizationId,
                prefix + " Category"
            );

        return insertProduct(
            organizationId,
            categoryId,
            prefix,
            prefix + " Product",
            "PACKAGED",
            true
        );
    }

    private UUID insertOwnedIngredient(
        String prefix,
        String unit,
        boolean active
    ) {

        return insertIngredient(
            organizationId,
            prefix,
            prefix + " Ingredient",
            unit,
            active
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
        String name
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
            "category-" + randomSuffix()
        );

        return id;
    }

    private UUID insertProduct(
        UUID tenantId,
        UUID categoryId,
        String skuPrefix,
        String name,
        String productType,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        boolean prepared =
            "PREPARED".equals(
                productType
            );

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
                ?,
                ?
            )
            """,
            id,
            tenantId,
            categoryId,
            skuPrefix + "-" + randomSuffix(),
            name,
            productType,
            prepared,
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

    private UUID insertIngredient(
        UUID tenantId,
        String codePrefix,
        String name,
        String unit,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO ingredients (
                id,
                organization_id,
                code,
                name,
                base_unit,
                is_active
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            id,
            tenantId,
            codePrefix + "-" + randomSuffix(),
            name,
            unit,
            active
        );

        return id;
    }

    private UUID insertAllergen(
        UUID tenantId,
        String codePrefix,
        String name,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO allergens (
                id,
                organization_id,
                code,
                name,
                description,
                is_active
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            id,
            tenantId,
            codePrefix + "-" + randomSuffix(),
            name,
            name + " description",
            active
        );

        return id;
    }

    private UUID insertOption(
        UUID productId,
        String optionName
    ) {

        UUID groupId =
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
            VALUES (?, ?, ?, 0, 1, FALSE, 0)
            """,
            groupId,
            productId,
            optionName + " Group"
        );

        UUID optionId =
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
            VALUES (?, ?, ?, 0, TRUE, 0)
            """,
            optionId,
            groupId,
            optionName
        );

        return optionId;
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);
    }
}