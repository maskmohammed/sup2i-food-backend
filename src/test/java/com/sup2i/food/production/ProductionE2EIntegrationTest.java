package com.sup2i.food.production;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.production.api.dto.CreateProductionRunCommand;
import com.sup2i.food.production.api.dto.CreateProductionRunItemCommand;
import com.sup2i.food.production.api.dto.ProductionRunResponse;
import com.sup2i.food.production.domain.ProductionRunStatus;
import com.sup2i.food.production.domain.ProductionTargetSource;
import com.sup2i.food.production.domain.ProductionType;
import com.sup2i.food.production.exception.ProductionConflictException;
import com.sup2i.food.production.exception.ProductionNotFoundException;
import com.sup2i.food.production.exception.ProductionValidationException;
import com.sup2i.food.production.service.ProductionService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.issuer=sup2i-food-backend",
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
@Testcontainers
class ProductionE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_production_test"
            );

    @Autowired
    private ProductionService
        productionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID organizationId;
    private UUID actorId;
    private UUID campusId;
    private UUID serviceLocationId;
    private UUID kitchenLocationId;
    private UUID stockLocationId;
    private UUID productId;
    private UUID ingredientId;
    private UUID recipeId;
    private UUID recipeItemId;
    private UUID stockItemId;
    private UUID defaultLotId;

    @BeforeEach
    void seedBaseFixture() {

        organizationId =
            insertOrganization(
                "PROD"
            );

        actorId =
            insertUser(
                organizationId,
                "ACTOR"
            );

        campusId =
            insertCampus(
                organizationId,
                "MAIN"
            );

        serviceLocationId =
            insertLocation(
                campusId,
                "SERVICE",
                "CANTEEN"
            );

        kitchenLocationId =
            insertLocation(
                campusId,
                "KITCHEN",
                "KITCHEN"
            );

        stockLocationId =
            insertStockLocation(
                kitchenLocationId
            );

        UUID categoryId =
            insertCategory(
                organizationId
            );

        productId =
            insertPreparedProduct(
                organizationId,
                categoryId
            );

        ingredientId =
            insertIngredient(
                organizationId
            );

        recipeId =
            insertRecipe(
                productId
            );

        recipeItemId =
            insertRecipeItem(
                recipeId,
                ingredientId,
                "2.000",
                "0.1000"
            );

        stockItemId =
            insertIngredientStockItem(
                organizationId,
                ingredientId
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "20.000",
            "0.000"
        );

        defaultLotId =
            insertLot(
                stockItemId,
                stockLocationId,
                "DEFAULT",
                OffsetDateTime.now()
                    .plusDays(10),
                "20.000"
            );
    }

    @Test
    void createRunPersistsPlannedTenantScopedItems() {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        assertThat(run.status())
            .isEqualTo(
                ProductionRunStatus.PLANNED
            );

        assertThat(run.organizationId())
            .isEqualTo(
                organizationId
            );

        assertThat(run.kitchenLocationId())
            .isEqualTo(
                kitchenLocationId
            );

        assertThat(run.items())
            .hasSize(1);

        assertThat(
            run.items()
                .get(0)
                .recipeId()
        ).isEqualTo(
            recipeId
        );

        assertThat(
            run.items()
                .get(0)
                .targetQuantity()
        ).isEqualByComparingTo(
            "3.000"
        );

        assertThat(
            movementCount(run.id())
        ).isZero();
    }

    @Test
    void foreignTenantCannotReadOrStartRun() {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN"
            );

        UUID foreignActor =
            insertUser(
                foreignOrganization,
                "FOREIGN"
            );

        assertThatThrownBy(() ->
            productionService.getRun(
                foreignActor,
                run.id()
            )
        ).isInstanceOf(
            ProductionNotFoundException.class
        );

        assertThatThrownBy(() ->
            productionService.startRun(
                foreignActor,
                run.id()
            )
        ).isInstanceOf(
            ProductionNotFoundException.class
        );

        assertThat(
            runStatus(run.id())
        ).isEqualTo(
            "PLANNED"
        );

        assertThat(
            movementCount(run.id())
        ).isZero();
    }

    @Test
    void startConsumesRecipeStockAndLinksMovement() {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        ProductionRunResponse started =
            productionService.startRun(
                actorId,
                run.id()
            );

        assertThat(started.status())
            .isEqualTo(
                ProductionRunStatus.IN_PROGRESS
            );

        assertThat(started.startedAt())
            .isNotNull();

        assertThat(
            started.items()
                .get(0)
                .preparationStartedAt()
        ).isNotNull();

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            "13.400"
        );

        assertThat(
            reservedQuantity()
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            lotRemaining(
                defaultLotId
            )
        ).isEqualByComparingTo(
            "13.400"
        );

        assertThat(
            movementCount(run.id())
        ).isEqualTo(1);

        assertThat(
            movementLinkCount(run.id())
        ).isEqualTo(1);

        assertThat(
            movementPhysicalDelta(
                run.id()
            )
        ).isEqualByComparingTo(
            "-6.600"
        );

        assertThat(
            movementReservedDelta(
                run.id()
            )
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            movementReferenceType(
                run.id()
            )
        ).isEqualTo(
            "PRODUCTION_RUN_ITEM"
        );

        assertThat(
            movementRole(
                run.id()
            )
        ).isEqualTo(
            "CONSUMPTION"
        );

        assertThat(
            movementLotDelta(
                run.id()
            )
        ).isEqualByComparingTo(
            "-6.600"
        );
    }

    @Test
    void replayDoesNotDoubleConsume() {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        ProductionRunResponse first =
            productionService.startRun(
                actorId,
                run.id()
            );

        BigDecimal physicalAfterFirst =
            physicalQuantity();

        ProductionRunResponse replay =
            productionService.startRun(
                actorId,
                run.id()
            );

        assertThat(first.status())
            .isEqualTo(
                ProductionRunStatus.IN_PROGRESS
            );

        assertThat(replay.status())
            .isEqualTo(
                ProductionRunStatus.IN_PROGRESS
            );

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            physicalAfterFirst
        );

        assertThat(
            movementCount(run.id())
        ).isEqualTo(1);

        assertThat(
            movementLinkCount(run.id())
        ).isEqualTo(1);
    }

    @Test
    void reservedStockCannotBeStolenAndStartRollsBack() {

        setBalance(
            "7.000",
            "2.000"
        );

        setDefaultLotRemaining(
            "7.000"
        );

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        assertThatThrownBy(() ->
            productionService.startRun(
                actorId,
                run.id()
            )
        )
            .isInstanceOf(
                ProductionConflictException.class
            )
            .hasMessageContaining(
                "unreserved"
            );

        assertThat(
            runStatus(run.id())
        ).isEqualTo(
            "PLANNED"
        );

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            "7.000"
        );

        assertThat(
            reservedQuantity()
        ).isEqualByComparingTo(
            "2.000"
        );

        assertThat(
            lotRemaining(
                defaultLotId
            )
        ).isEqualByComparingTo(
            "7.000"
        );

        assertThat(
            movementCount(run.id())
        ).isZero();

        assertThat(
            movementLinkCount(run.id())
        ).isZero();
    }

    @Test
    void startUsesFefoLotsWithinKitchenStockLocation() {

        jdbcTemplate.update(
            """
            UPDATE recipe_items
            SET
                quantity = 1.000,
                waste_factor = 0.0000
            WHERE id = ?
            """,
            recipeItemId
        );

        jdbcTemplate.update(
            """
            DELETE FROM stock_lots
            WHERE id = ?
            """,
            defaultLotId
        );

        setBalance(
            "7.000",
            "0.000"
        );

        UUID earlyLot =
            insertLot(
                stockItemId,
                stockLocationId,
                "EARLY",
                OffsetDateTime.now()
                    .plusDays(1),
                "2.000"
            );

        UUID laterLot =
            insertLot(
                stockItemId,
                stockLocationId,
                "LATER",
                OffsetDateTime.now()
                    .plusDays(5),
                "5.000"
            );

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        productionService.startRun(
            actorId,
            run.id()
        );

        assertThat(
            lotRemaining(
                earlyLot
            )
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            lotRemaining(
                laterLot
            )
        ).isEqualByComparingTo(
            "4.000"
        );

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            "4.000"
        );

        assertThat(
            movementLotRowCount(
                run.id()
            )
        ).isEqualTo(2);
    }

    @Test
    void concurrentStartConsumesExactlyOnce()
        throws Exception {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        CountDownLatch ready =
            new CountDownLatch(2);

        CountDownLatch start =
            new CountDownLatch(1);

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        try {

            Future<ProductionRunResponse> first =
                executor.submit(() -> {

                    ready.countDown();

                    boolean released =
                        start.await(
                            10,
                            TimeUnit.SECONDS
                        );

                    if (!released) {
                        throw new IllegalStateException(
                            "Concurrent start latch timed out."
                        );
                    }

                    return productionService.startRun(
                        actorId,
                        run.id()
                    );
                });

            Future<ProductionRunResponse> second =
                executor.submit(() -> {

                    ready.countDown();

                    boolean released =
                        start.await(
                            10,
                            TimeUnit.SECONDS
                        );

                    if (!released) {
                        throw new IllegalStateException(
                            "Concurrent start latch timed out."
                        );
                    }

                    return productionService.startRun(
                        actorId,
                        run.id()
                    );
                });

            assertThat(
                ready.await(
                    10,
                    TimeUnit.SECONDS
                )
            ).isTrue();

            start.countDown();

            ProductionRunResponse firstResult =
                first.get(
                    30,
                    TimeUnit.SECONDS
                );

            ProductionRunResponse secondResult =
                second.get(
                    30,
                    TimeUnit.SECONDS
                );

            assertThat(firstResult.status())
                .isEqualTo(
                    ProductionRunStatus.IN_PROGRESS
                );

            assertThat(secondResult.status())
                .isEqualTo(
                    ProductionRunStatus.IN_PROGRESS
                );

        } finally {

            executor.shutdownNow();
        }

        assertThat(
            movementCount(run.id())
        ).isEqualTo(1);

        assertThat(
            movementLinkCount(run.id())
        ).isEqualTo(1);

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            "13.400"
        );

        assertThat(
            lotRemaining(
                defaultLotId
            )
        ).isEqualByComparingTo(
            "13.400"
        );
    }

    @Test
    void recipeOutputUnitConversionIsRejectedUntilNormative() {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.KILOGRAM
            );

        assertThatThrownBy(() ->
            productionService.startRun(
                actorId,
                run.id()
            )
        )
            .isInstanceOf(
                ProductionValidationException.class
            )
            .hasMessageContaining(
                "PIECE"
            );

        assertThat(
            runStatus(run.id())
        ).isEqualTo(
            "PLANNED"
        );

        assertThat(
            movementCount(run.id())
        ).isZero();

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            "20.000"
        );
    }

    @Test
    void completePersistsActualYieldAndCompletionMetadata() {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        productionService.startRun(
            actorId,
            run.id()
        );

        UUID runItemId =
            runItemId(
                run.id()
            );

        BigDecimal physicalAfterStart =
            physicalQuantity();

        ProductionRunResponse completed =
            productionService.completeRun(
                actorId,
                run.id(),
                Map.of(
                    runItemId,
                    new BigDecimal("2.500")
                )
            );

        assertThat(completed.status())
            .isEqualTo(
                ProductionRunStatus.COMPLETED
            );

        assertThat(
            preparedQuantity(
                run.id()
            )
        ).isEqualByComparingTo(
            "2.500"
        );

        assertThat(
            preparationCompletedAt(
                run.id()
            )
        ).isNotNull();

        assertThat(
            runCompletedAt(
                run.id()
            )
        ).isNotNull();

        assertThat(
            runApprovedBy(
                run.id()
            )
        ).isEqualTo(
            actorId
        );

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            physicalAfterStart
        );

        assertThat(
            movementCount(
                run.id()
            )
        ).isEqualTo(1);
    }

    @Test
    void completionAllowsYieldAboveTargetBecauseSchemaOnlyRequiresNonNegative() {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        productionService.startRun(
            actorId,
            run.id()
        );

        UUID runItemId =
            runItemId(
                run.id()
            );

        ProductionRunResponse completed =
            productionService.completeRun(
                actorId,
                run.id(),
                Map.of(
                    runItemId,
                    new BigDecimal("3.500")
                )
            );

        assertThat(completed.status())
            .isEqualTo(
                ProductionRunStatus.COMPLETED
            );

        assertThat(
            preparedQuantity(
                run.id()
            )
        ).isEqualByComparingTo(
            "3.500"
        );
    }

    @Test
    void completionReplayIsStableAndDifferentYieldConflicts() {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        productionService.startRun(
            actorId,
            run.id()
        );

        UUID runItemId =
            runItemId(
                run.id()
            );

        Map<UUID, BigDecimal> yield =
            Map.of(
                runItemId,
                new BigDecimal("2.750")
            );

        ProductionRunResponse first =
            productionService.completeRun(
                actorId,
                run.id(),
                yield
            );

        OffsetDateTime firstCompletedAt =
            runCompletedAt(
                run.id()
            );

        ProductionRunResponse replay =
            productionService.completeRun(
                actorId,
                run.id(),
                yield
            );

        assertThat(first.status())
            .isEqualTo(
                ProductionRunStatus.COMPLETED
            );

        assertThat(replay.status())
            .isEqualTo(
                ProductionRunStatus.COMPLETED
            );

        assertThat(
            runCompletedAt(
                run.id()
            )
        ).isEqualTo(
            firstCompletedAt
        );

        assertThat(
            preparedQuantity(
                run.id()
            )
        ).isEqualByComparingTo(
            "2.750"
        );

        assertThat(
            movementCount(
                run.id()
            )
        ).isEqualTo(1);

        assertThatThrownBy(() ->
            productionService.completeRun(
                actorId,
                run.id(),
                Map.of(
                    runItemId,
                    new BigDecimal("2.000")
                )
            )
        )
            .isInstanceOf(
                ProductionConflictException.class
            )
            .hasMessageContaining(
                "different"
            );

        assertThat(
            preparedQuantity(
                run.id()
            )
        ).isEqualByComparingTo(
            "2.750"
        );
    }

    @Test
    void completionRequiresExactNonNegativeYieldSet() {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        productionService.startRun(
            actorId,
            run.id()
        );

        UUID runItemId =
            runItemId(
                run.id()
            );

        assertThatThrownBy(() ->
            productionService.completeRun(
                actorId,
                run.id(),
                Map.of()
            )
        )
            .isInstanceOf(
                ProductionValidationException.class
            );

        assertThatThrownBy(() ->
            productionService.completeRun(
                actorId,
                run.id(),
                Map.of(
                    runItemId,
                    new BigDecimal("-0.001")
                )
            )
        )
            .isInstanceOf(
                ProductionValidationException.class
            )
            .hasMessageContaining(
                "negative"
            );

        assertThatThrownBy(() ->
            productionService.completeRun(
                actorId,
                run.id(),
                Map.of(
                    UUID.randomUUID(),
                    BigDecimal.ONE
                )
            )
        )
            .isInstanceOf(
                ProductionValidationException.class
            );

        assertThat(
            runStatus(
                run.id()
            )
        ).isEqualTo(
            "IN_PROGRESS"
        );

        assertThat(
            preparedQuantity(
                run.id()
            )
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            movementCount(
                run.id()
            )
        ).isEqualTo(1);
    }

    @Test
    void plannedCancelIsStableAndDoesNotMutateInventory() {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        BigDecimal physicalBefore =
            physicalQuantity();

        BigDecimal lotBefore =
            lotRemaining(
                defaultLotId
            );

        ProductionRunResponse cancelled =
            productionService.cancelRun(
                actorId,
                run.id()
            );

        OffsetDateTime firstCancelledAt =
            runCancelledAt(
                run.id()
            );

        ProductionRunResponse replay =
            productionService.cancelRun(
                actorId,
                run.id()
            );

        assertThat(cancelled.status())
            .isEqualTo(
                ProductionRunStatus.CANCELLED
            );

        assertThat(replay.status())
            .isEqualTo(
                ProductionRunStatus.CANCELLED
            );

        assertThat(
            runCancelledAt(
                run.id()
            )
        ).isEqualTo(
            firstCancelledAt
        );

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            physicalBefore
        );

        assertThat(
            lotRemaining(
                defaultLotId
            )
        ).isEqualByComparingTo(
            lotBefore
        );

        assertThat(
            movementCount(
                run.id()
            )
        ).isZero();

        assertThat(
            preparedQuantity(
                run.id()
            )
        ).isEqualByComparingTo(
            "0.000"
        );
    }

    @Test
    void startedRunCannotBeCancelledWithoutExplicitReturnOrWasteWorkflow() {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        productionService.startRun(
            actorId,
            run.id()
        );

        BigDecimal physicalAfterStart =
            physicalQuantity();

        BigDecimal lotAfterStart =
            lotRemaining(
                defaultLotId
            );

        assertThatThrownBy(() ->
            productionService.cancelRun(
                actorId,
                run.id()
            )
        )
            .isInstanceOf(
                ProductionConflictException.class
            )
            .hasMessageContaining(
                "return or waste"
            );

        assertThat(
            runStatus(
                run.id()
            )
        ).isEqualTo(
            "IN_PROGRESS"
        );

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            physicalAfterStart
        );

        assertThat(
            lotRemaining(
                defaultLotId
            )
        ).isEqualByComparingTo(
            lotAfterStart
        );

        assertThat(
            movementCount(
                run.id()
            )
        ).isEqualTo(1);
    }

    @Test
    void concurrentCompletionWithSameYieldIsExactlyOnceAndStable()
        throws Exception {

        ProductionRunResponse run =
            createRun(
                actorId,
                new BigDecimal("3.000"),
                MeasurementUnit.PIECE
            );

        productionService.startRun(
            actorId,
            run.id()
        );

        UUID runItemId =
            runItemId(
                run.id()
            );

        Map<UUID, BigDecimal> yield =
            Map.of(
                runItemId,
                new BigDecimal("2.500")
            );

        CountDownLatch ready =
            new CountDownLatch(2);

        CountDownLatch start =
            new CountDownLatch(1);

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        try {

            Future<ProductionRunResponse> first =
                executor.submit(() -> {

                    ready.countDown();

                    boolean released =
                        start.await(
                            10,
                            TimeUnit.SECONDS
                        );

                    if (!released) {
                        throw new IllegalStateException(
                            "Concurrent completion latch timed out."
                        );
                    }

                    return productionService.completeRun(
                        actorId,
                        run.id(),
                        yield
                    );
                });

            Future<ProductionRunResponse> second =
                executor.submit(() -> {

                    ready.countDown();

                    boolean released =
                        start.await(
                            10,
                            TimeUnit.SECONDS
                        );

                    if (!released) {
                        throw new IllegalStateException(
                            "Concurrent completion latch timed out."
                        );
                    }

                    return productionService.completeRun(
                        actorId,
                        run.id(),
                        yield
                    );
                });

            assertThat(
                ready.await(
                    10,
                    TimeUnit.SECONDS
                )
            ).isTrue();

            start.countDown();

            ProductionRunResponse firstResult =
                first.get(
                    30,
                    TimeUnit.SECONDS
                );

            ProductionRunResponse secondResult =
                second.get(
                    30,
                    TimeUnit.SECONDS
                );

            assertThat(firstResult.status())
                .isEqualTo(
                    ProductionRunStatus.COMPLETED
                );

            assertThat(secondResult.status())
                .isEqualTo(
                    ProductionRunStatus.COMPLETED
                );

        } finally {

            executor.shutdownNow();
        }

        assertThat(
            runStatus(
                run.id()
            )
        ).isEqualTo(
            "COMPLETED"
        );

        assertThat(
            preparedQuantity(
                run.id()
            )
        ).isEqualByComparingTo(
            "2.500"
        );

        assertThat(
            movementCount(
                run.id()
            )
        ).isEqualTo(1);

        assertThat(
            movementLinkCount(
                run.id()
            )
        ).isEqualTo(1);
    }

    private ProductionRunResponse createRun(
        UUID userId,
        BigDecimal targetQuantity,
        MeasurementUnit outputUnit
    ) {

        return productionService.createRun(
            userId,
            new CreateProductionRunCommand(
                campusId,
                serviceLocationId,
                kitchenLocationId,
                null,
                null,
                LocalDate.now(),
                ProductionType.CANTEEN_BATCH,
                ProductionTargetSource.MANUAL,
                "Production E2E",
                List.of(
                    new CreateProductionRunItemCommand(
                        productId,
                        null,
                        recipeId,
                        targetQuantity,
                        outputUnit,
                        null,
                        "Prepared batch"
                    )
                )
            )
        );
    }

    private UUID insertOrganization(
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            suffix();

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
            prefix + " Organization " + suffix,
            prefix + suffix
        );

        return id;
    }

    private UUID insertUser(
        UUID tenantId,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            suffix();

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
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                'ACTIVE'
            )
            """,
            id,
            tenantId,
            "production-"
                + suffix
                + "@sup2i.test",
            prefix,
            "User"
        );

        return id;
    }

    private UUID insertCampus(
        UUID tenantId,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            suffix();

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
            prefix + " Campus",
            prefix + suffix
        );

        return id;
    }

    private UUID insertLocation(
        UUID ownerCampusId,
        String prefix,
        String type
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            suffix();

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
            VALUES (?, ?, ?, ?, ?, TRUE)
            """,
            id,
            ownerCampusId,
            prefix + " Location",
            prefix + suffix,
            type
        );

        return id;
    }

    private UUID insertStockLocation(
        UUID locationId
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_locations (
                id,
                location_id,
                name,
                type,
                is_active
            )
            VALUES (
                ?,
                ?,
                ?,
                'KITCHEN',
                TRUE
            )
            """,
            id,
            locationId,
            "Production Kitchen Stock"
        );

        return id;
    }

    private UUID insertCategory(
        UUID tenantId
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            suffix();

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
            VALUES (
                ?,
                ?,
                ?,
                ?,
                0,
                TRUE
            )
            """,
            id,
            tenantId,
            "Production Category",
            "production-" + suffix
        );

        return id;
    }

    private UUID insertPreparedProduct(
        UUID tenantId,
        UUID categoryId
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            suffix();

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
                'PREPARED',
                10.00,
                0.00,
                5,
                FALSE,
                TRUE,
                TRUE
            )
            """,
            id,
            tenantId,
            categoryId,
            "PROD-" + suffix,
            "Production Product " + suffix
        );

        return id;
    }

    private UUID insertIngredient(
        UUID tenantId
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO ingredients (
                id,
                organization_id,
                code,
                name,
                base_unit,
                is_active,
                track_stock
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                'GRAM',
                TRUE,
                TRUE
            )
            """,
            id,
            tenantId,
            "ING-" + suffix,
            "Production Ingredient " + suffix
        );

        return id;
    }

    private UUID insertRecipe(
        UUID ownerProductId
    ) {

        UUID id =
            UUID.randomUUID();

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
                NULL,
                1,
                TRUE,
                ?
            )
            """,
            id,
            ownerProductId,
            OffsetDateTime.now()
                .minusDays(2)
        );

        return id;
    }

    private UUID insertRecipeItem(
        UUID ownerRecipeId,
        UUID ownerIngredientId,
        String quantity,
        String wasteFactor
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO recipe_items (
                id,
                recipe_id,
                ingredient_id,
                quantity,
                unit,
                waste_factor,
                is_critical
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                'GRAM',
                ?,
                TRUE
            )
            """,
            id,
            ownerRecipeId,
            ownerIngredientId,
            new BigDecimal(quantity),
            new BigDecimal(wasteFactor)
        );

        return id;
    }

    private UUID insertIngredientStockItem(
        UUID tenantId,
        UUID ownerIngredientId
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_items (
                id,
                organization_id,
                ingredient_id,
                base_unit,
                low_stock_threshold,
                track_expiry
            )
            VALUES (
                ?,
                ?,
                ?,
                'GRAM',
                1.000,
                TRUE
            )
            """,
            id,
            tenantId,
            ownerIngredientId
        );

        return id;
    }

    private void insertBalance(
        UUID ownerStockItemId,
        UUID ownerStockLocationId,
        String physical,
        String reserved
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO stock_balances (
                stock_item_id,
                stock_location_id,
                physical_quantity,
                reserved_quantity
            )
            VALUES (?, ?, ?, ?)
            """,
            ownerStockItemId,
            ownerStockLocationId,
            new BigDecimal(physical),
            new BigDecimal(reserved)
        );
    }

    private UUID insertLot(
        UUID ownerStockItemId,
        UUID ownerStockLocationId,
        String lotNumber,
        OffsetDateTime expiresAt,
        String quantity
    ) {

        UUID id =
            UUID.randomUUID();

        BigDecimal amount =
            new BigDecimal(quantity);

        jdbcTemplate.update(
            """
            INSERT INTO stock_lots (
                id,
                stock_item_id,
                stock_location_id,
                lot_number,
                received_at,
                expires_at,
                quantity_received,
                quantity_remaining
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?
            )
            """,
            id,
            ownerStockItemId,
            ownerStockLocationId,
            lotNumber,
            OffsetDateTime.now()
                .minusDays(1),
            expiresAt,
            amount,
            amount
        );

        return id;
    }

    private void setBalance(
        String physical,
        String reserved
    ) {

        jdbcTemplate.update(
            """
            UPDATE stock_balances
            SET
                physical_quantity = ?,
                reserved_quantity = ?
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            new BigDecimal(physical),
            new BigDecimal(reserved),
            stockItemId,
            stockLocationId
        );
    }

    private void setDefaultLotRemaining(
        String remaining
    ) {

        jdbcTemplate.update(
            """
            UPDATE stock_lots
            SET quantity_remaining = ?
            WHERE id = ?
            """,
            new BigDecimal(remaining),
            defaultLotId
        );
    }

    private BigDecimal physicalQuantity() {

        return jdbcTemplate.queryForObject(
            """
            SELECT physical_quantity
            FROM stock_balances
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            BigDecimal.class,
            stockItemId,
            stockLocationId
        );
    }

    private BigDecimal reservedQuantity() {

        return jdbcTemplate.queryForObject(
            """
            SELECT reserved_quantity
            FROM stock_balances
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            BigDecimal.class,
            stockItemId,
            stockLocationId
        );
    }

    private BigDecimal lotRemaining(
        UUID lotId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT quantity_remaining
            FROM stock_lots
            WHERE id = ?
            """,
            BigDecimal.class,
            lotId
        );
    }

    private UUID runItemId(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM production_run_items
            WHERE production_run_id = ?
            """,
            UUID.class,
            runId
        );
    }

    private BigDecimal preparedQuantity(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT prepared_quantity
            FROM production_run_items
            WHERE production_run_id = ?
            """,
            BigDecimal.class,
            runId
        );
    }

    private OffsetDateTime preparationCompletedAt(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT preparation_completed_at
            FROM production_run_items
            WHERE production_run_id = ?
            """,
            OffsetDateTime.class,
            runId
        );
    }

    private OffsetDateTime runCompletedAt(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT completed_at
            FROM production_runs
            WHERE id = ?
            """,
            OffsetDateTime.class,
            runId
        );
    }

    private OffsetDateTime runCancelledAt(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT cancelled_at
            FROM production_runs
            WHERE id = ?
            """,
            OffsetDateTime.class,
            runId
        );
    }

    private UUID runApprovedBy(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT approved_by
            FROM production_runs
            WHERE id = ?
            """,
            UUID.class,
            runId
        );
    }

    private String runStatus(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM production_runs
            WHERE id = ?
            """,
            String.class,
            runId
        );
    }

    private Integer movementCount(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_movements im
            JOIN production_run_movements prm
              ON prm.inventory_movement_id =
                 im.id
            JOIN production_run_items pri
              ON pri.id =
                 prm.production_run_item_id
            WHERE pri.production_run_id = ?
              AND im.movement_type =
                  'RECIPE_CONSUMPTION'
            """,
            Integer.class,
            runId
        );
    }

    private Integer movementLinkCount(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM production_run_movements prm
            JOIN production_run_items pri
              ON pri.id =
                 prm.production_run_item_id
            WHERE pri.production_run_id = ?
              AND prm.movement_role =
                  'CONSUMPTION'
            """,
            Integer.class,
            runId
        );
    }

    private BigDecimal movementPhysicalDelta(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT im.physical_delta
            FROM inventory_movements im
            JOIN production_run_movements prm
              ON prm.inventory_movement_id =
                 im.id
            JOIN production_run_items pri
              ON pri.id =
                 prm.production_run_item_id
            WHERE pri.production_run_id = ?
            """,
            BigDecimal.class,
            runId
        );
    }

    private BigDecimal movementReservedDelta(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT im.reserved_delta
            FROM inventory_movements im
            JOIN production_run_movements prm
              ON prm.inventory_movement_id =
                 im.id
            JOIN production_run_items pri
              ON pri.id =
                 prm.production_run_item_id
            WHERE pri.production_run_id = ?
            """,
            BigDecimal.class,
            runId
        );
    }

    private String movementReferenceType(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT im.reference_type
            FROM inventory_movements im
            JOIN production_run_movements prm
              ON prm.inventory_movement_id =
                 im.id
            JOIN production_run_items pri
              ON pri.id =
                 prm.production_run_item_id
            WHERE pri.production_run_id = ?
            """,
            String.class,
            runId
        );
    }

    private String movementRole(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT prm.movement_role
            FROM production_run_movements prm
            JOIN production_run_items pri
              ON pri.id =
                 prm.production_run_item_id
            WHERE pri.production_run_id = ?
            """,
            String.class,
            runId
        );
    }

    private BigDecimal movementLotDelta(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT iml.quantity_delta
            FROM inventory_movement_lots iml
            JOIN production_run_movements prm
              ON prm.inventory_movement_id =
                 iml.inventory_movement_id
            JOIN production_run_items pri
              ON pri.id =
                 prm.production_run_item_id
            WHERE pri.production_run_id = ?
            """,
            BigDecimal.class,
            runId
        );
    }

    private Integer movementLotRowCount(
        UUID runId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_movement_lots iml
            JOIN production_run_movements prm
              ON prm.inventory_movement_id =
                 iml.inventory_movement_id
            JOIN production_run_items pri
              ON pri.id =
                 prm.production_run_item_id
            WHERE pri.production_run_id = ?
            """,
            Integer.class,
            runId
        );
    }

    private String suffix() {

        return UUID.randomUUID()
            .toString()
            .replace(
                "-",
                ""
            )
            .substring(
                0,
                10
            );
    }
}