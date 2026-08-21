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

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class CatalogMetadataE2EIntegrationTest {

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
            "catalog-metadata-"
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
            "Catalog Metadata " + suffix,
            "META" + suffix
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
            "Metadata"
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "catalog-metadata-e2e",
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
    // LOCATION SETTINGS - SECURITY
    // =========================================================

    @Test
    void locationWriteRequiresProductWritePermission()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "LOC-RBAC-W"
            );

        UUID locationId =
            insertOwnedLocation(
                "LOC-RBAC-W"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/locations/{locationId}",
                    productId,
                    locationId
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
                          "enabled": true,
                          "allowedDays": [1,2,3]
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
    void locationReadRequiresCatalogReadPermission()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "LOC-RBAC-R"
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/locations",
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
    // LOCATION SETTINGS - SMALLINT[] / UPSERT
    // =========================================================

    @Test
    void upsertLocationRoundTripsSmallintArraySorted()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "LOC-ARRAY"
            );

        UUID locationId =
            insertOwnedLocation(
                "LOC-ARRAY"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/locations/{locationId}",
                    productId,
                    locationId
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
                          "enabled": true,
                          "allowedDays": [5,1,3],
                          "availableFromTime": "08:30:00",
                          "availableToTime": "18:45:00",
                          "preparationMinutes": 12
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.locationId")
                    .value(locationId.toString())
            )
            .andExpect(
                jsonPath("$.enabled")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.allowedDays[0]")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.allowedDays[1]")
                    .value(3)
            )
            .andExpect(
                jsonPath("$.allowedDays[2]")
                    .value(5)
            )
            .andExpect(
                jsonPath("$.preparationMinutes")
                    .value(12)
            );

        String databaseArray =
            jdbcTemplate.queryForObject(
                """
                SELECT allowed_days::text
                FROM product_location_settings
                WHERE product_id = ?
                  AND location_id = ?
                """,
                String.class,
                productId,
                locationId
            );

        assertThat(databaseArray)
            .isEqualTo("{1,3,5}");

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/locations",
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
                jsonPath("$[0].locationId")
                    .value(locationId.toString())
            )
            .andExpect(
                jsonPath("$[0].allowedDays[0]")
                    .value(1)
            )
            .andExpect(
                jsonPath("$[0].allowedDays[1]")
                    .value(3)
            )
            .andExpect(
                jsonPath("$[0].allowedDays[2]")
                    .value(5)
            );
    }

    @Test
    void upsertLocationUpdatesExistingRow()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "LOC-UPDATE"
            );

        UUID locationId =
            insertOwnedLocation(
                "LOC-UPDATE"
            );

        putLocationSetting(
            productId,
            locationId,
            """
            {
              "enabled": true,
              "allowedDays": [1,2],
              "preparationMinutes": 10
            }
            """
        );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/locations/{locationId}",
                    productId,
                    locationId
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
                          "enabled": false,
                          "allowedDays": [4,5],
                          "preparationMinutes": 20
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.enabled")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.allowedDays[0]")
                    .value(4)
            )
            .andExpect(
                jsonPath("$.allowedDays[1]")
                    .value(5)
            )
            .andExpect(
                jsonPath("$.preparationMinutes")
                    .value(20)
            );

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM product_location_settings
                WHERE product_id = ?
                  AND location_id = ?
                """,
                Long.class,
                productId,
                locationId
            );

        assertThat(count)
            .isEqualTo(1L);
    }

    @Test
    void locationFromAnotherTenantIsRejected()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "LOC-FOREIGN"
            );

        UUID otherOrganization =
            insertOrganization(
                "FOREIGNLOC"
            );

        UUID foreignLocation =
            insertLocation(
                insertCampus(
                    otherOrganization,
                    "Foreign Campus"
                ),
                "Foreign Location"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/locations/{locationId}",
                    productId,
                    foreignLocation
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
                          "enabled": true,
                          "allowedDays": [1]
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
    void invalidAllowedDayReturnsValidationError()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "LOC-DAY-INVALID"
            );

        UUID locationId =
            insertOwnedLocation(
                "LOC-DAY-INVALID"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/locations/{locationId}",
                    productId,
                    locationId
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
                          "allowedDays": [0,8]
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
    void duplicateAllowedDayReturnsValidationError()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "LOC-DAY-DUP"
            );

        UUID locationId =
            insertOwnedLocation(
                "LOC-DAY-DUP"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/locations/{locationId}",
                    productId,
                    locationId
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
                          "allowedDays": [1,2,2]
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
    void invalidLocationTimeRangeReturnsValidationError()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "LOC-TIME"
            );

        UUID locationId =
            insertOwnedLocation(
                "LOC-TIME"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/locations/{locationId}",
                    productId,
                    locationId
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
                          "availableFromTime": "18:00:00",
                          "availableToTime": "08:00:00"
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
    void locationReadCannotCrossProductTenant()
        throws Exception {

        UUID otherOrganization =
            insertOrganization(
                "LOC-PRODUCT-FOREIGN"
            );

        UUID categoryId =
            insertCategory(
                otherOrganization,
                "Foreign Category"
            );

        UUID foreignProduct =
            insertProduct(
                otherOrganization,
                categoryId,
                "FOREIGN-LOC-PRODUCT",
                true
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/locations",
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

    // =========================================================
    // DIETARY METADATA - SECURITY
    // =========================================================

    @Test
    void dietaryMetadataWriteRequiresProductWritePermission()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "DIET-RBAC-W"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/dietary-metadata",
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
                          "allergens": [],
                          "dietaryTags": []
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
    void dietaryMetadataReadRequiresCatalogReadPermission()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "DIET-RBAC-R"
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/dietary-metadata",
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
    // REFERENTIALS
    // =========================================================

    @Test
    void referentialsReturnOnlyActiveCurrentTenantValues()
        throws Exception {

        UUID activeAllergen =
            insertAllergen(
                organizationId,
                "GLUTEN",
                "Gluten",
                true
            );

        insertAllergen(
            organizationId,
            "HIDDEN",
            "Hidden allergen",
            false
        );

        UUID activeTag =
            insertDietaryTag(
                organizationId,
                "VEGAN",
                "Vegan",
                true
            );

        insertDietaryTag(
            organizationId,
            "HIDDEN-TAG",
            "Hidden tag",
            false
        );

        UUID otherOrganization =
            insertOrganization(
                "REF-OTHER"
            );

        insertAllergen(
            otherOrganization,
            "OTHER-ALLERGEN",
            "Other allergen",
            true
        );

        insertDietaryTag(
            otherOrganization,
            "OTHER-TAG",
            "Other tag",
            true
        );

        String allergensBody =
            mockMvc.perform(
                    get(
                        "/api/v1/catalog/referentials/allergens"
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
                    jsonPath("$[0].id")
                        .value(
                            activeAllergen.toString()
                        )
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(allergensBody)
            .contains("Gluten")
            .doesNotContain("Hidden allergen")
            .doesNotContain("Other allergen");

        String tagsBody =
            mockMvc.perform(
                    get(
                        "/api/v1/catalog/referentials/dietary-tags"
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
                    jsonPath("$[0].id")
                        .value(
                            activeTag.toString()
                        )
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(tagsBody)
            .contains("Vegan")
            .doesNotContain("Hidden tag")
            .doesNotContain("Other tag");
    }

    // =========================================================
    // DIETARY METADATA - WRITE / READ
    // =========================================================

    @Test
    void replaceDietaryMetadataPersistsNormalizedLinksAndNotes()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "DIET-001"
            );

        UUID allergenId =
            insertAllergen(
                organizationId,
                "PEANUT",
                "Peanut",
                true
            );

        UUID tagId =
            insertDietaryTag(
                organizationId,
                "VEGETARIAN",
                "Vegetarian",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/dietary-metadata",
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
                          "allergens": [
                            {
                              "referenceId": "%s",
                              "note": "  Contains traces  "
                            }
                          ],
                          "dietaryTags": [
                            {
                              "referenceId": "%s",
                              "note": "  Kitchen validated  "
                            }
                          ]
                        }
                        """.formatted(
                            allergenId,
                            tagId
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.allergens[0].id")
                    .value(allergenId.toString())
            )
            .andExpect(
                jsonPath("$.allergens[0].note")
                    .value("Contains traces")
            )
            .andExpect(
                jsonPath("$.dietaryTags[0].id")
                    .value(tagId.toString())
            )
            .andExpect(
                jsonPath("$.dietaryTags[0].note")
                    .value("Kitchen validated")
            );

        String allergenNote =
            jdbcTemplate.queryForObject(
                """
                SELECT note
                FROM product_allergens
                WHERE product_id = ?
                  AND allergen_id = ?
                """,
                String.class,
                productId,
                allergenId
            );

        String tagNote =
            jdbcTemplate.queryForObject(
                """
                SELECT note
                FROM product_dietary_tags
                WHERE product_id = ?
                  AND dietary_tag_id = ?
                """,
                String.class,
                productId,
                tagId
            );

        assertThat(allergenNote)
            .isEqualTo("Contains traces");

        assertThat(tagNote)
            .isEqualTo("Kitchen validated");

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/dietary-metadata",
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
                    .value(productId.toString())
            )
            .andExpect(
                jsonPath("$.allergens[0].name")
                    .value("Peanut")
            )
            .andExpect(
                jsonPath("$.dietaryTags[0].name")
                    .value("Vegetarian")
            );
    }

    @Test
    void duplicateAllergenReferenceReturnsValidationError()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "DIET-DUP-A"
            );

        UUID allergenId =
            insertAllergen(
                organizationId,
                "DUP-A",
                "Duplicate A",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/dietary-metadata",
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
                          "allergens": [
                            {"referenceId": "%s"},
                            {"referenceId": "%s"}
                          ],
                          "dietaryTags": []
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
    void duplicateDietaryTagReferenceReturnsValidationError()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "DIET-DUP-T"
            );

        UUID tagId =
            insertDietaryTag(
                organizationId,
                "DUP-T",
                "Duplicate Tag",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/dietary-metadata",
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
                          "allergens": [],
                          "dietaryTags": [
                            {"referenceId": "%s"},
                            {"referenceId": "%s"}
                          ]
                        }
                        """.formatted(
                            tagId,
                            tagId
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
    void foreignAllergenIsRejected()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "DIET-FOREIGN-A"
            );

        UUID otherOrganization =
            insertOrganization(
                "FOREIGN-A"
            );

        UUID foreignAllergen =
            insertAllergen(
                otherOrganization,
                "FOREIGN",
                "Foreign",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/dietary-metadata",
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
                          "allergens": [
                            {"referenceId": "%s"}
                          ],
                          "dietaryTags": []
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
    void foreignDietaryTagIsRejected()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "DIET-FOREIGN-T"
            );

        UUID otherOrganization =
            insertOrganization(
                "FOREIGN-T"
            );

        UUID foreignTag =
            insertDietaryTag(
                otherOrganization,
                "FOREIGN-TAG",
                "Foreign tag",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/dietary-metadata",
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
                          "allergens": [],
                          "dietaryTags": [
                            {"referenceId": "%s"}
                          ]
                        }
                        """.formatted(
                            foreignTag
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
    void inactiveAllergenIsRejected()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "DIET-INACTIVE-A"
            );

        UUID allergenId =
            insertAllergen(
                organizationId,
                "INACTIVE-A",
                "Inactive allergen",
                false
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/dietary-metadata",
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
                          "allergens": [
                            {"referenceId": "%s"}
                          ],
                          "dietaryTags": []
                        }
                        """.formatted(
                            allergenId
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
    void inactiveDietaryTagIsRejected()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "DIET-INACTIVE-T"
            );

        UUID tagId =
            insertDietaryTag(
                organizationId,
                "INACTIVE-T",
                "Inactive tag",
                false
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/dietary-metadata",
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
                          "allergens": [],
                          "dietaryTags": [
                            {"referenceId": "%s"}
                          ]
                        }
                        """.formatted(
                            tagId
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
    void emptyListsClearExistingDietaryMetadata()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "DIET-CLEAR"
            );

        UUID allergenId =
            insertAllergen(
                organizationId,
                "CLEAR-A",
                "Clear allergen",
                true
            );

        UUID tagId =
            insertDietaryTag(
                organizationId,
                "CLEAR-T",
                "Clear tag",
                true
            );

        putDietaryMetadata(
            productId,
            """
            {
              "allergens": [
                {"referenceId": "%s"}
              ],
              "dietaryTags": [
                {"referenceId": "%s"}
              ]
            }
            """.formatted(
                allergenId,
                tagId
            )
        );

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/dietary-metadata",
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
                          "allergens": [],
                          "dietaryTags": []
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
            )
            .andExpect(
                jsonPath("$.dietaryTags.length()")
                    .value(0)
            );

        Long allergens =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM product_allergens
                WHERE product_id = ?
                """,
                Long.class,
                productId
            );

        Long tags =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM product_dietary_tags
                WHERE product_id = ?
                """,
                Long.class,
                productId
            );

        assertThat(allergens)
            .isZero();

        assertThat(tags)
            .isZero();
    }

    @Test
    void inactiveProductLocationsReturnProductUnavailable()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Inactive Location Product Category"
            );

        UUID productId =
            insertProduct(
                organizationId,
                categoryId,
                "LOC-INACTIVE-P",
                false
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/locations",
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

    @Test
    void productUnderInactiveCategoryLocationsReturnProductUnavailable()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Inactive Location Category"
            );

        UUID productId =
            insertProduct(
                organizationId,
                categoryId,
                "LOC-INACTIVE-CAT",
                true
            );

        jdbcTemplate.update(
            """
            UPDATE categories
            SET is_active = FALSE
            WHERE id = ?
            """,
            categoryId
        );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/locations",
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
    @Test
    void inactiveProductDietaryMetadataReturnsProductUnavailable()
        throws Exception {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Inactive Product Category"
            );

        UUID productId =
            insertProduct(
                organizationId,
                categoryId,
                "DIET-INACTIVE-P",
                false
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/dietary-metadata",
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

    @Test
    void dietaryMetadataCannotCrossTenantBoundary()
        throws Exception {

        UUID otherOrganization =
            insertOrganization(
                "DIET-PRODUCT-FOREIGN"
            );

        UUID categoryId =
            insertCategory(
                otherOrganization,
                "Foreign Dietary Category"
            );

        UUID foreignProduct =
            insertProduct(
                otherOrganization,
                categoryId,
                "FOREIGN-DIET-P",
                true
            );

        mockMvc.perform(
                get(
                    "/api/v1/catalog/products/{productId}/dietary-metadata",
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

    // =========================================================
    // HTTP HELPERS
    // =========================================================

    private void putLocationSetting(
        UUID productId,
        UUID locationId,
        String body
    ) throws Exception {

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/locations/{locationId}",
                    productId,
                    locationId
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
                status().isOk()
            );
    }

    private void putDietaryMetadata(
        UUID productId,
        String body
    ) throws Exception {

        mockMvc.perform(
                put(
                    "/api/v1/admin/products/{productId}/dietary-metadata",
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
                status().isOk()
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
        String prefix
    ) {

        UUID categoryId =
            insertCategory(
                organizationId,
                "Category " + prefix
            );

        return insertProduct(
            organizationId,
            categoryId,
            prefix,
            true
        );
    }

    private UUID insertOwnedLocation(
        String prefix
    ) {

        UUID campusId =
            insertCampus(
                organizationId,
                "Campus " + prefix
            );

        return insertLocation(
            campusId,
            "Location " + prefix
        );
    }

    private UUID insertOrganization(
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

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
            (
                prefix
                    .replaceAll(
                        "[^A-Za-z0-9]",
                        ""
                    )
                    .substring(
                        0,
                        Math.min(
                            20,
                            prefix.replaceAll(
                                "[^A-Za-z0-9]",
                                ""
                            ).length()
                        )
                    )
                + suffix
            )
        );

        return id;
    }

    private UUID insertCampus(
        UUID tenantId,
        String name
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO campuses (
                id,
                organization_id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, ?, TRUE)
            """,
            id,
            tenantId,
            name,
            "CAMP" + randomSuffix()
        );

        return id;
    }

    private UUID insertLocation(
        UUID campusId,
        String name
    ) {

        UUID id =
            UUID.randomUUID();

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
            VALUES (?, ?, ?, ?, 'SNACK', TRUE)
            """,
            id,
            campusId,
            name,
            "LOC" + randomSuffix()
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
            skuPrefix + "-" + randomSuffix(),
            skuPrefix + " Product",
            active
        );

        return id;
    }

    private UUID insertAllergen(
        UUID tenantId,
        String code,
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
            code + "-" + randomSuffix(),
            name,
            name + " description",
            active
        );

        return id;
    }

    private UUID insertDietaryTag(
        UUID tenantId,
        String code,
        String name,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO dietary_tags (
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
            code + "-" + randomSuffix(),
            name,
            name + " description",
            active
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