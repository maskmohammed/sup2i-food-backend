package com.sup2i.food.interaction;

import com.sup2i.food.forecast.api.dto.CreateDemandForecastCommand;
import com.sup2i.food.forecast.api.dto.DemandForecastResponse;
import com.sup2i.food.forecast.domain.ForecastSubjectType;
import com.sup2i.food.forecast.exception.DemandForecastConflictException;
import com.sup2i.food.forecast.exception.DemandForecastNotFoundException;
import com.sup2i.food.forecast.exception.DemandForecastValidationException;
import com.sup2i.food.forecast.service.DemandForecastService;

import com.sup2i.food.interaction.api.dto.CreateProductInteractionCommand;
import com.sup2i.food.interaction.api.dto.ProductInteractionResponse;
import com.sup2i.food.interaction.domain.ProductInteractionType;
import com.sup2i.food.interaction.exception.ProductInteractionConflictException;
import com.sup2i.food.interaction.exception.ProductInteractionNotFoundException;
import com.sup2i.food.interaction.exception.ProductInteractionValidationException;
import com.sup2i.food.interaction.service.ProductInteractionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
@Testcontainers
class ProductInteractionForecastE2EIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductInteractionService interactionService;

    @Autowired
    private DemandForecastService forecastService;

    private TenantSeed primary;
    private TenantSeed other;

    @BeforeEach
    void seedTenants() {

        primary =
            seedTenant(
                "a"
            );

        other =
            seedTenant(
                "b"
            );
    }

    @Test
    void interactionPersistsExactlyAllTenSchemaTypes() {

        for (
            ProductInteractionType type :
                ProductInteractionType.values()
        ) {

            ProductInteractionResponse response =
                interactionService.record(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateProductInteractionCommand(
                        primary.studentId(),
                        primary.productId(),
                        type,
                        null,
                        null,
                        primary.locationId(),
                        null,
                        null
                    )
                );

            assertThat(response.eventType())
                .isEqualTo(
                    type
                );

            assertThat(response.replayed())
                .isFalse();
        }

        List<String> stored =
            jdbcTemplate.queryForList(
                """
                SELECT event_type
                FROM product_interaction_events
                WHERE product_id = ?
                ORDER BY event_type
                """,
                String.class,
                primary.productId()
            );

        Set<String> expected =
            List.of(
                ProductInteractionType.values()
            )
                .stream()
                .map(
                    Enum::name
                )
                .collect(
                    Collectors.toSet()
                );

        assertThat(stored)
            .hasSize(
                10
            );

        assertThat(
            Set.copyOf(
                stored
            )
        )
            .isEqualTo(
                expected
            );
    }

    @Test
    void interactionReplayPreservesTimestampAndCanonicalJson() {

        UUID interactionId =
            UUID.randomUUID();

        OffsetDateTime occurredAt =
            OffsetDateTime.now()
                .withNano(
                    123456789
                );

        CreateProductInteractionCommand command =
            new CreateProductInteractionCommand(
                primary.studentId(),
                primary.productId(),
                ProductInteractionType.VIEW,
                null,
                null,
                primary.locationId(),
                occurredAt,
                """
                {
                  "z": 1,
                  "a": true
                }
                """
            );

        ProductInteractionResponse created =
            interactionService.record(
                primary.organizationId(),
                interactionId,
                command
            );

        assertThat(created.replayed())
            .isFalse();

        ProductInteractionResponse replay =
            interactionService.record(
                primary.organizationId(),
                interactionId,
                command
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThat(
            interactionService.listForProduct(
                primary.organizationId(),
                primary.productId(),
                20
            )
        )
            .extracting(
                ProductInteractionResponse::id
            )
            .contains(
                interactionId
            );

        assertThat(
            interactionService.listForStudent(
                primary.organizationId(),
                primary.studentId(),
                20
            )
        )
            .extracting(
                ProductInteractionResponse::id
            )
            .contains(
                interactionId
            );

        assertThatThrownBy(
            () ->
                interactionService.record(
                    primary.organizationId(),
                    interactionId,
                    new CreateProductInteractionCommand(
                        primary.studentId(),
                        primary.productId(),
                        ProductInteractionType.VIEW,
                        null,
                        null,
                        primary.locationId(),
                        occurredAt,
                        "{\"different\":true}"
                    )
                )
        )
            .isInstanceOf(
                ProductInteractionConflictException.class
            );

        assertThatThrownBy(
            () ->
                interactionService.get(
                    other.organizationId(),
                    interactionId
                )
        )
            .isInstanceOf(
                ProductInteractionNotFoundException.class
            );
    }

    @Test
    void interactionOptionalReferencesAreTenantGuarded() {

        UUID interactionId =
            UUID.randomUUID();

        ProductInteractionResponse created =
            interactionService.record(
                primary.organizationId(),
                interactionId,
                new CreateProductInteractionCommand(
                    primary.studentId(),
                    primary.productId(),
                    ProductInteractionType.ORDER,
                    primary.cartId(),
                    primary.orderId(),
                    primary.locationId(),
                    null,
                    "{\"source\":\"e2e\"}"
                )
            );

        assertThat(created.studentId())
            .isEqualTo(
                primary.studentId()
            );

        assertThat(created.cartId())
            .isEqualTo(
                primary.cartId()
            );

        assertThat(created.orderId())
            .isEqualTo(
                primary.orderId()
            );

        assertThat(created.locationId())
            .isEqualTo(
                primary.locationId()
            );

        assertThatThrownBy(
            () ->
                interactionService.record(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateProductInteractionCommand(
                        other.studentId(),
                        primary.productId(),
                        ProductInteractionType.VIEW,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                ProductInteractionNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                interactionService.record(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateProductInteractionCommand(
                        primary.studentId(),
                        primary.productId(),
                        ProductInteractionType.CART_ADD,
                        other.cartId(),
                        null,
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                ProductInteractionNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                interactionService.record(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateProductInteractionCommand(
                        primary.studentId(),
                        primary.productId(),
                        ProductInteractionType.ORDER,
                        null,
                        other.orderId(),
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                ProductInteractionNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                interactionService.record(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateProductInteractionCommand(
                        primary.studentId(),
                        primary.productId(),
                        ProductInteractionType.VIEW,
                        null,
                        null,
                        other.locationId(),
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                ProductInteractionNotFoundException.class
            );
    }

    @Test
    void interactionValidationRejectsMissingCrossTenantAndInvalidJson() {

        assertThatThrownBy(
            () ->
                interactionService.record(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateProductInteractionCommand(
                        primary.studentId(),
                        null,
                        ProductInteractionType.VIEW,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                ProductInteractionValidationException.class
            );

        assertThatThrownBy(
            () ->
                interactionService.record(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateProductInteractionCommand(
                        primary.studentId(),
                        other.productId(),
                        ProductInteractionType.VIEW,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                ProductInteractionNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                interactionService.record(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateProductInteractionCommand(
                        primary.studentId(),
                        primary.productId(),
                        ProductInteractionType.VIEW,
                        null,
                        null,
                        null,
                        null,
                        "{broken"
                    )
                )
        )
            .isInstanceOf(
                ProductInteractionValidationException.class
            );
    }

    @Test
    void forecastIngestsProductAndIngredientAndReplaysCanonicalJson() {

        LocalDate forecastDate =
            LocalDate.now()
                .plusDays(
                    2
                );

        UUID productForecastId =
            UUID.randomUUID();

        CreateDemandForecastCommand productCommand =
            new CreateDemandForecastCommand(
                primary.locationId(),
                ForecastSubjectType.PRODUCT,
                primary.productId(),
                forecastDate,
                primary.timeSlotId(),
                new BigDecimal(
                    "12.500"
                ),
                new BigDecimal(
                    "0.87500"
                ),
                "external-model",
                "v1",
                """
                {
                  "weather": "warm",
                  "day": 2
                }
                """
            );

        DemandForecastResponse created =
            forecastService.ingest(
                primary.organizationId(),
                productForecastId,
                productCommand
            );

        assertThat(created.replayed())
            .isFalse();

        assertThat(created.subjectType())
            .isEqualTo(
                ForecastSubjectType.PRODUCT
            );

        DemandForecastResponse replay =
            forecastService.ingest(
                primary.organizationId(),
                productForecastId,
                productCommand
            );

        assertThat(replay.replayed())
            .isTrue();

        Map<String, Object> productStored =
            jdbcTemplate.queryForMap(
                """
                SELECT product_id, ingredient_id
                FROM demand_forecasts
                WHERE id = ?
                """,
                productForecastId
            );

        assertThat(productStored.get("product_id"))
            .isEqualTo(
                primary.productId()
            );

        assertThat(productStored.get("ingredient_id"))
            .isNull();

        UUID ingredientForecastId =
            UUID.randomUUID();

        DemandForecastResponse ingredient =
            forecastService.ingest(
                primary.organizationId(),
                ingredientForecastId,
                new CreateDemandForecastCommand(
                    primary.locationId(),
                    ForecastSubjectType.INGREDIENT,
                    primary.ingredientId(),
                    forecastDate,
                    null,
                    new BigDecimal(
                        "4.250"
                    ),
                    null,
                    null,
                    null,
                    null
                )
            );

        assertThat(ingredient.subjectType())
            .isEqualTo(
                ForecastSubjectType.INGREDIENT
            );

        Map<String, Object> ingredientStored =
            jdbcTemplate.queryForMap(
                """
                SELECT product_id, ingredient_id
                FROM demand_forecasts
                WHERE id = ?
                """,
                ingredientForecastId
            );

        assertThat(ingredientStored.get("product_id"))
            .isNull();

        assertThat(ingredientStored.get("ingredient_id"))
            .isEqualTo(
                primary.ingredientId()
            );

        assertThatThrownBy(
            () ->
                forecastService.ingest(
                    primary.organizationId(),
                    productForecastId,
                    new CreateDemandForecastCommand(
                        primary.locationId(),
                        ForecastSubjectType.PRODUCT,
                        primary.productId(),
                        forecastDate,
                        primary.timeSlotId(),
                        new BigDecimal(
                            "13.500"
                        ),
                        new BigDecimal(
                            "0.875"
                        ),
                        "external-model",
                        "v1",
                        "{\"day\":2,\"weather\":\"warm\"}"
                    )
                )
        )
            .isInstanceOf(
                DemandForecastConflictException.class
            );
    }

    @Test
    void forecastValidationEnforcesQuantityConfidenceAndJson() {

        LocalDate date =
            LocalDate.now()
                .plusDays(
                    2
                );

        assertThatThrownBy(
            () ->
                forecastService.ingest(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateDemandForecastCommand(
                        primary.locationId(),
                        ForecastSubjectType.PRODUCT,
                        primary.productId(),
                        date,
                        null,
                        new BigDecimal(
                            "-0.001"
                        ),
                        null,
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                DemandForecastValidationException.class
            );

        assertThatThrownBy(
            () ->
                forecastService.ingest(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateDemandForecastCommand(
                        primary.locationId(),
                        ForecastSubjectType.PRODUCT,
                        primary.productId(),
                        date,
                        null,
                        BigDecimal.ONE,
                        new BigDecimal(
                            "1.00001"
                        ),
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                DemandForecastValidationException.class
            );

        assertThatThrownBy(
            () ->
                forecastService.ingest(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateDemandForecastCommand(
                        primary.locationId(),
                        null,
                        primary.productId(),
                        date,
                        null,
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                DemandForecastValidationException.class
            );

        assertThatThrownBy(
            () ->
                forecastService.ingest(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateDemandForecastCommand(
                        primary.locationId(),
                        ForecastSubjectType.PRODUCT,
                        primary.productId(),
                        date,
                        null,
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        "{broken"
                    )
                )
        )
            .isInstanceOf(
                DemandForecastValidationException.class
            );
    }

    @Test
    void forecastTenantIsolationGuardsLocationSubjectSlotAndRead() {

        LocalDate date =
            LocalDate.now()
                .plusDays(
                    2
                );

        assertThatThrownBy(
            () ->
                forecastService.ingest(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateDemandForecastCommand(
                        other.locationId(),
                        ForecastSubjectType.PRODUCT,
                        primary.productId(),
                        date,
                        null,
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                DemandForecastNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                forecastService.ingest(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateDemandForecastCommand(
                        primary.locationId(),
                        ForecastSubjectType.PRODUCT,
                        other.productId(),
                        date,
                        null,
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                DemandForecastNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                forecastService.ingest(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateDemandForecastCommand(
                        primary.locationId(),
                        ForecastSubjectType.INGREDIENT,
                        other.ingredientId(),
                        date,
                        null,
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                DemandForecastNotFoundException.class
            );

        assertThatThrownBy(
            () ->
                forecastService.ingest(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    new CreateDemandForecastCommand(
                        primary.locationId(),
                        ForecastSubjectType.PRODUCT,
                        primary.productId(),
                        date,
                        other.timeSlotId(),
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        null
                    )
                )
        )
            .isInstanceOf(
                DemandForecastNotFoundException.class
            );

        UUID forecastId =
            UUID.randomUUID();

        forecastService.ingest(
            primary.organizationId(),
            forecastId,
            new CreateDemandForecastCommand(
                primary.locationId(),
                ForecastSubjectType.PRODUCT,
                primary.productId(),
                date,
                primary.timeSlotId(),
                BigDecimal.ONE,
                null,
                null,
                null,
                null
            )
        );

        assertThatThrownBy(
            () ->
                forecastService.get(
                    other.organizationId(),
                    forecastId
                )
        )
            .isInstanceOf(
                DemandForecastNotFoundException.class
            );
    }

    @Test
    void forecastReportingReadsLocationDateAndBothSubjectTypes() {

        LocalDate date1 =
            LocalDate.now()
                .plusDays(
                    2
                );

        LocalDate date2 =
            date1.plusDays(
                1
            );

        UUID productOne =
            UUID.randomUUID();

        UUID productTwo =
            UUID.randomUUID();

        UUID ingredientOne =
            UUID.randomUUID();

        forecastService.ingest(
            primary.organizationId(),
            productOne,
            new CreateDemandForecastCommand(
                primary.locationId(),
                ForecastSubjectType.PRODUCT,
                primary.productId(),
                date1,
                primary.timeSlotId(),
                new BigDecimal(
                    "10"
                ),
                new BigDecimal(
                    "0.8"
                ),
                "m",
                "1",
                null
            )
        );

        forecastService.ingest(
            primary.organizationId(),
            productTwo,
            new CreateDemandForecastCommand(
                primary.locationId(),
                ForecastSubjectType.PRODUCT,
                primary.productId(),
                date2,
                null,
                new BigDecimal(
                    "11"
                ),
                null,
                null,
                null,
                null
            )
        );

        forecastService.ingest(
            primary.organizationId(),
            ingredientOne,
            new CreateDemandForecastCommand(
                primary.locationId(),
                ForecastSubjectType.INGREDIENT,
                primary.ingredientId(),
                date1,
                null,
                new BigDecimal(
                    "3.5"
                ),
                null,
                null,
                null,
                null
            )
        );

        List<DemandForecastResponse> locationDate =
            forecastService.listForLocationAndDate(
                primary.organizationId(),
                primary.locationId(),
                date1,
                20
            );

        assertThat(locationDate)
            .extracting(
                DemandForecastResponse::id
            )
            .containsExactlyInAnyOrder(
                productOne,
                ingredientOne
            );

        List<DemandForecastResponse> products =
            forecastService.listForSubject(
                primary.organizationId(),
                ForecastSubjectType.PRODUCT,
                primary.productId(),
                date1,
                date2,
                20
            );

        assertThat(products)
            .extracting(
                DemandForecastResponse::id
            )
            .containsExactlyInAnyOrder(
                productOne,
                productTwo
            );

        List<DemandForecastResponse> ingredients =
            forecastService.listForSubject(
                primary.organizationId(),
                ForecastSubjectType.INGREDIENT,
                primary.ingredientId(),
                date1,
                date2,
                20
            );

        assertThat(ingredients)
            .extracting(
                DemandForecastResponse::id
            )
            .containsExactly(
                ingredientOne
            );
    }

    private TenantSeed seedTenant(
        String prefix
    ) {

        String suffix =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                )
                .substring(
                    0,
                    10
                );

        UUID organizationId =
            UUID.randomUUID();

        UUID campusId =
            UUID.randomUUID();

        UUID locationId =
            UUID.randomUUID();

        UUID userId =
            UUID.randomUUID();

        UUID studentId =
            UUID.randomUUID();

        UUID categoryId =
            UUID.randomUUID();

        UUID productId =
            UUID.randomUUID();

        UUID ingredientId =
            UUID.randomUUID();

        UUID timeSlotId =
            UUID.randomUUID();

        UUID cartId =
            UUID.randomUUID();

        UUID orderId =
            UUID.randomUUID();

        LocalDate futureDate =
            LocalDate.now()
                .plusDays(
                    2
                );

        jdbcTemplate.update(
            """
            INSERT INTO organizations(
                id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            organizationId,
            "B20 Organization " + suffix,
            "B20O" + prefix + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO campuses(
                id,
                organization_id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, ?, TRUE)
            """,
            campusId,
            organizationId,
            "B20 Campus " + suffix,
            "B20C" + prefix + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO locations(
                id,
                campus_id,
                name,
                code,
                type,
                is_active
            )
            VALUES (?, ?, ?, ?, 'SNACK', TRUE)
            """,
            locationId,
            campusId,
            "B20 Location " + suffix,
            "B20L" + prefix + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO users(
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
            "b20-" + prefix + "-" + suffix + "@sup2i.test",
            "B20",
            "User"
        );

        jdbcTemplate.update(
            """
            INSERT INTO students(
                id,
                user_id,
                campus_id,
                student_number,
                enrollment_status
            )
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """,
            studentId,
            userId,
            campusId,
            "B20S" + prefix + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO categories(
                id,
                organization_id,
                name,
                slug,
                display_order,
                is_active
            )
            VALUES (?, ?, ?, ?, 0, TRUE)
            """,
            categoryId,
            organizationId,
            "B20 Category " + suffix,
            "b20-category-" + prefix + "-" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO products(
                id,
                organization_id,
                category_id,
                sku,
                name,
                product_type,
                base_price,
                tax_rate,
                track_stock,
                is_prepared,
                is_active
            )
            VALUES(
                ?, ?, ?, ?, ?,
                'PACKAGED',
                10.00,
                0.00,
                TRUE,
                FALSE,
                TRUE
            )
            """,
            productId,
            organizationId,
            categoryId,
            "B20SKU" + prefix + suffix,
            "B20 Product " + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO ingredients(
                id,
                organization_id,
                code,
                name,
                base_unit,
                is_active
            )
            VALUES (?, ?, ?, ?, 'GRAM', TRUE)
            """,
            ingredientId,
            organizationId,
            "B20I" + prefix + suffix,
            "B20 Ingredient " + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO time_slots(
                id,
                location_id,
                slot_date,
                start_time,
                end_time,
                capacity,
                reserved_count,
                status
            )
            VALUES (?, ?, ?, ?, ?, 100, 0, 'OPEN')
            """,
            timeSlotId,
            locationId,
            futureDate,
            LocalTime.of(
                12,
                0
            ),
            LocalTime.of(
                12,
                30
            )
        );

        jdbcTemplate.update(
            """
            INSERT INTO shopping_carts(
                id,
                student_id,
                location_id,
                status,
                currency
            )
            VALUES (?, ?, ?, 'ACTIVE', 'MAD')
            """,
            cartId,
            studentId,
            locationId
        );

        jdbcTemplate.update(
            """
            INSERT INTO orders(
                id,
                organization_id,
                campus_id,
                location_id,
                student_id,
                order_number,
                business_date,
                source,
                status,
                subtotal,
                discount_total,
                total,
                currency
            )
            VALUES(
                ?, ?, ?, ?, ?, ?,
                ?, 'MOBILE', 'CREATED',
                0, 0, 0, 'MAD'
            )
            """,
            orderId,
            organizationId,
            campusId,
            locationId,
            studentId,
            "B20-" + prefix + "-" + suffix,
            LocalDate.now()
        );

        return new TenantSeed(
            organizationId,
            campusId,
            locationId,
            userId,
            studentId,
            categoryId,
            productId,
            ingredientId,
            timeSlotId,
            cartId,
            orderId
        );
    }

    private record TenantSeed(
        UUID organizationId,
        UUID campusId,
        UUID locationId,
        UUID userId,
        UUID studentId,
        UUID categoryId,
        UUID productId,
        UUID ingredientId,
        UUID timeSlotId,
        UUID cartId,
        UUID orderId
    ) {
    }
}