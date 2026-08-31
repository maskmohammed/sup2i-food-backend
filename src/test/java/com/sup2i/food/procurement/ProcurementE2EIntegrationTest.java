package com.sup2i.food.procurement;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.procurement.api.dto.PurchaseOrderCommand;
import com.sup2i.food.procurement.api.dto.PurchaseOrderItemCommand;
import com.sup2i.food.procurement.api.dto.PurchaseOrderReceiptCommand;
import com.sup2i.food.procurement.api.dto.PurchaseOrderReceiptLineCommand;
import com.sup2i.food.procurement.api.dto.PurchaseOrderResponse;
import com.sup2i.food.procurement.api.dto.SupplierCommand;
import com.sup2i.food.procurement.api.dto.SupplierResponse;
import com.sup2i.food.procurement.domain.PurchaseOrderStatus;
import com.sup2i.food.procurement.exception.ProcurementConflictException;
import com.sup2i.food.procurement.exception.ProcurementNotFoundException;
import com.sup2i.food.procurement.exception.ProcurementValidationException;
import com.sup2i.food.procurement.service.PurchaseOrderService;
import com.sup2i.food.procurement.service.SupplierService;
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
import java.util.List;
import java.util.UUID;

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
class ProcurementE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_procurement_test"
            );

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private PurchaseOrderService
        purchaseOrderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID organizationId;
    private UUID actorId;
    private UUID campusId;
    private UUID locationId;
    private UUID stockLocationId;
    private UUID ingredientId;
    private UUID stockItemId;
    private UUID supplierId;

    @BeforeEach
    void seedBaseFixture() {

        organizationId =
            insertOrganization(
                "PROC"
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

        locationId =
            insertLocation(
                campusId,
                "STORAGE"
            );

        stockLocationId =
            insertStockLocation(
                locationId
            );

        ingredientId =
            insertIngredient(
                organizationId
            );

        stockItemId =
            insertIngredientStockItem(
                organizationId,
                ingredientId
            );

        supplierId =
            UUID.randomUUID();

        supplierService.create(
            actorId,
            supplierId,
            supplierCommand(
                "Base Supplier"
            )
        );
    }

    @Test
    void supplierLifecycleAndTenantIsolationAreStable() {

        UUID supplier =
            UUID.randomUUID();

        SupplierCommand original =
            supplierCommand(
                "Lifecycle Supplier"
            );

        SupplierResponse first =
            supplierService.create(
                actorId,
                supplier,
                original
            );

        SupplierResponse replay =
            supplierService.create(
                actorId,
                supplier,
                original
            );

        assertThat(first.id())
            .isEqualTo(supplier);

        assertThat(replay.id())
            .isEqualTo(supplier);

        assertThat(replay.active())
            .isTrue();

        SupplierCommand updatedCommand =
            new SupplierCommand(
                "Lifecycle Supplier Updated",
                "+212600000001",
                "updated-" + suffix() + "@sup2i.test",
                "Updated address"
            );

        SupplierResponse updated =
            supplierService.update(
                actorId,
                supplier,
                updatedCommand
            );

        assertThat(updated.name())
            .isEqualTo(
                "Lifecycle Supplier Updated"
            );

        SupplierResponse inactive =
            supplierService.setActive(
                actorId,
                supplier,
                false
            );

        assertThat(inactive.active())
            .isFalse();

        assertThat(
            supplierService.list(
                actorId,
                true
            )
        )
            .extracting(
                SupplierResponse::id
            )
            .doesNotContain(
                supplier
            );

        SupplierResponse reactivated =
            supplierService.setActive(
                actorId,
                supplier,
                true
            );

        assertThat(reactivated.active())
            .isTrue();

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-SUP"
            );

        UUID foreignActor =
            insertUser(
                foreignOrganization,
                "FOREIGN"
            );

        assertThatThrownBy(() ->
            supplierService.get(
                foreignActor,
                supplier
            )
        )
            .isInstanceOf(
                ProcurementNotFoundException.class
            );
    }

    @Test
    void purchaseOrderCreateReplayLifecycleAndTenantIsolationAreStable() {

        UUID purchaseOrderId =
            UUID.randomUUID();

        PurchaseOrderCommand command =
            purchaseOrderCommand(
                "10.000",
                MeasurementUnit.GRAM
            );

        PurchaseOrderResponse created =
            purchaseOrderService.create(
                actorId,
                purchaseOrderId,
                command
            );

        PurchaseOrderResponse replay =
            purchaseOrderService.create(
                actorId,
                purchaseOrderId,
                command
            );

        assertThat(created.status())
            .isEqualTo(
                PurchaseOrderStatus.DRAFT
            );

        assertThat(replay.id())
            .isEqualTo(
                purchaseOrderId
            );

        assertThat(replay.items())
            .hasSize(1);

        assertThat(
            replay.items()
                .get(0)
                .receivedQuantity()
        ).isEqualByComparingTo(
            "0.000"
        );

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-PO"
            );

        UUID foreignActor =
            insertUser(
                foreignOrganization,
                "FOREIGN"
            );

        assertThatThrownBy(() ->
            purchaseOrderService.get(
                foreignActor,
                purchaseOrderId
            )
        )
            .isInstanceOf(
                ProcurementNotFoundException.class
            );

        PurchaseOrderResponse submitted =
            purchaseOrderService.submit(
                actorId,
                purchaseOrderId
            );

        assertThat(submitted.status())
            .isEqualTo(
                PurchaseOrderStatus.SUBMITTED
            );

        assertThat(
            purchaseOrderService.submit(
                actorId,
                purchaseOrderId
            ).status()
        ).isEqualTo(
            PurchaseOrderStatus.SUBMITTED
        );

        PurchaseOrderResponse approved =
            purchaseOrderService.approve(
                actorId,
                purchaseOrderId
            );

        assertThat(approved.status())
            .isEqualTo(
                PurchaseOrderStatus.APPROVED
            );

        assertThat(approved.approvedBy())
            .isEqualTo(
                actorId
            );

        assertThat(
            purchaseOrderService.approve(
                actorId,
                purchaseOrderId
            ).status()
        ).isEqualTo(
            PurchaseOrderStatus.APPROVED
        );

        PurchaseOrderResponse ordered =
            purchaseOrderService.markOrdered(
                actorId,
                purchaseOrderId
            );

        assertThat(ordered.status())
            .isEqualTo(
                PurchaseOrderStatus.ORDERED
            );

        assertThat(
            purchaseOrderService.markOrdered(
                actorId,
                purchaseOrderId
            ).status()
        ).isEqualTo(
            PurchaseOrderStatus.ORDERED
        );
    }

    @Test
    void preOrderCancellationIsStableAndOrderedCancellationRequiresReversal() {

        UUID cancellableId =
            UUID.randomUUID();

        purchaseOrderService.create(
            actorId,
            cancellableId,
            purchaseOrderCommand(
                "5.000",
                MeasurementUnit.GRAM
            )
        );

        purchaseOrderService.submit(
            actorId,
            cancellableId
        );

        PurchaseOrderResponse cancelled =
            purchaseOrderService.cancel(
                actorId,
                cancellableId
            );

        assertThat(cancelled.status())
            .isEqualTo(
                PurchaseOrderStatus.CANCELLED
            );

        assertThat(
            purchaseOrderService.cancel(
                actorId,
                cancellableId
            ).status()
        ).isEqualTo(
            PurchaseOrderStatus.CANCELLED
        );

        PurchaseOrderResponse ordered =
            createOrderedOrder(
                "5.000"
            );

        assertThatThrownBy(() ->
            purchaseOrderService.cancel(
                actorId,
                ordered.id()
            )
        )
            .isInstanceOf(
                ProcurementConflictException.class
            )
            .hasMessageContaining(
                "return or reversal"
            );

        assertThat(
            purchaseOrderService.get(
                actorId,
                ordered.id()
            ).status()
        ).isEqualTo(
            PurchaseOrderStatus.ORDERED
        );
    }

    @Test
    void procurementUnitConversionIsRejectedUntilNormative() {

        UUID purchaseOrderId =
            UUID.randomUUID();

        assertThatThrownBy(() ->
            purchaseOrderService.create(
                actorId,
                purchaseOrderId,
                purchaseOrderCommand(
                    "2.000",
                    MeasurementUnit.KILOGRAM
                )
            )
        )
            .isInstanceOf(
                ProcurementValidationException.class
            )
            .hasMessageContaining(
                "base unit"
            );

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM purchase_orders
                WHERE id = ?
                """,
                Long.class,
                purchaseOrderId
            );

        assertThat(count)
            .isZero();
    }

    @Test
    void partialThenFullReceiptUpdatesStockAndV026Links() {

        PurchaseOrderResponse ordered =
            createOrderedOrder(
                "10.000"
            );

        UUID itemId =
            ordered.items()
                .get(0)
                .id();

        UUID firstReceipt =
            UUID.randomUUID();

        UUID firstLine =
            UUID.randomUUID();

        PurchaseOrderResponse partial =
            purchaseOrderService.receive(
                actorId,
                ordered.id(),
                receiptCommand(
                    firstReceipt,
                    firstLine,
                    itemId,
                    "4.000"
                )
            );

        assertThat(partial.status())
            .isEqualTo(
                PurchaseOrderStatus.PARTIALLY_RECEIVED
            );

        assertThat(
            partial.items()
                .get(0)
                .receivedQuantity()
        ).isEqualByComparingTo(
            "4.000"
        );

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            "4.000"
        );

        assertThat(
            receiptPurchaseOrderId(
                firstReceipt
            )
        ).isEqualTo(
            ordered.id()
        );

        assertThat(
            receiptLinePurchaseOrderItemId(
                firstLine
            )
        ).isEqualTo(
            itemId
        );

        assertThat(
            receiptMovementCount(
                firstReceipt
            )
        ).isEqualTo(1L);

        UUID secondReceipt =
            UUID.randomUUID();

        UUID secondLine =
            UUID.randomUUID();

        PurchaseOrderResponse completed =
            purchaseOrderService.receive(
                actorId,
                ordered.id(),
                receiptCommand(
                    secondReceipt,
                    secondLine,
                    itemId,
                    "6.000"
                )
            );

        assertThat(completed.status())
            .isEqualTo(
                PurchaseOrderStatus.RECEIVED
            );

        assertThat(
            completed.items()
                .get(0)
                .receivedQuantity()
        ).isEqualByComparingTo(
            "10.000"
        );

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            "10.000"
        );

        assertThat(
            receiptPurchaseOrderId(
                secondReceipt
            )
        ).isEqualTo(
            ordered.id()
        );

        assertThat(
            receiptLinePurchaseOrderItemId(
                secondLine
            )
        ).isEqualTo(
            itemId
        );

        assertThat(
            totalPurchaseInMovements(
                ordered.id()
            )
        ).isEqualTo(2L);
    }

    @Test
    void overReceiptRollsBackBeforeInventoryMutation() {

        PurchaseOrderResponse ordered =
            createOrderedOrder(
                "5.000"
            );

        UUID itemId =
            ordered.items()
                .get(0)
                .id();

        UUID receiptId =
            UUID.randomUUID();

        assertThatThrownBy(() ->
            purchaseOrderService.receive(
                actorId,
                ordered.id(),
                receiptCommand(
                    receiptId,
                    UUID.randomUUID(),
                    itemId,
                    "6.000"
                )
            )
        )
            .isInstanceOf(
                ProcurementConflictException.class
            )
            .hasMessageContaining(
                "exceeds ordered quantity"
            );

        assertThat(
            stockReceiptCount(
                receiptId
            )
        ).isZero();

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            purchaseOrderService.get(
                actorId,
                ordered.id()
            ).status()
        ).isEqualTo(
            PurchaseOrderStatus.ORDERED
        );
    }

    @Test
    void identicalPurchaseOrderReceiptReplayDoesNotDoubleStock() {

        PurchaseOrderResponse ordered =
            createOrderedOrder(
                "5.000"
            );

        UUID itemId =
            ordered.items()
                .get(0)
                .id();

        UUID receiptId =
            UUID.randomUUID();

        UUID lineId =
            UUID.randomUUID();

        PurchaseOrderReceiptCommand command =
            receiptCommand(
                receiptId,
                lineId,
                itemId,
                "5.000"
            );

        PurchaseOrderResponse first =
            purchaseOrderService.receive(
                actorId,
                ordered.id(),
                command
            );

        PurchaseOrderResponse replay =
            purchaseOrderService.receive(
                actorId,
                ordered.id(),
                command
            );

        assertThat(first.status())
            .isEqualTo(
                PurchaseOrderStatus.RECEIVED
            );

        assertThat(replay.status())
            .isEqualTo(
                PurchaseOrderStatus.RECEIVED
            );

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            "5.000"
        );

        assertThat(
            receiptMovementCount(
                receiptId
            )
        ).isEqualTo(1L);

        assertThat(
            stockReceiptCount(
                receiptId
            )
        ).isEqualTo(1L);

        assertThat(
            receiptPurchaseOrderId(
                receiptId
            )
        ).isEqualTo(
            ordered.id()
        );

        assertThat(
            receiptLinePurchaseOrderItemId(
                lineId
            )
        ).isEqualTo(
            itemId
        );
    }

    @Test
    void foreignCampusStockLocationIsRejectedWithoutInventoryMutation() {

        PurchaseOrderResponse ordered =
            createOrderedOrder(
                "3.000"
            );

        UUID secondCampus =
            insertCampus(
                organizationId,
                "SECOND"
            );

        UUID secondLocation =
            insertLocation(
                secondCampus,
                "SECOND-STORAGE"
            );

        UUID secondStockLocation =
            insertStockLocation(
                secondLocation
            );

        UUID receiptId =
            UUID.randomUUID();

        PurchaseOrderReceiptCommand command =
            new PurchaseOrderReceiptCommand(
                receiptId,
                secondStockLocation,
                "FOREIGN-CAMPUS-" + suffix(),
                "Must fail",
                List.of(
                    new PurchaseOrderReceiptLineCommand(
                        UUID.randomUUID(),
                        ordered.items()
                            .get(0)
                            .id(),
                        new BigDecimal("1.000"),
                        new BigDecimal("2.50"),
                        "FOREIGN-CAMPUS-LOT",
                        null
                    )
                )
            );

        assertThatThrownBy(() ->
            purchaseOrderService.receive(
                actorId,
                ordered.id(),
                command
            )
        )
            .isInstanceOf(
                ProcurementNotFoundException.class
            )
            .hasMessageContaining(
                "campus"
            );

        assertThat(
            stockReceiptCount(
                receiptId
            )
        ).isZero();

        assertThat(
            physicalQuantity()
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            purchaseOrderService.get(
                actorId,
                ordered.id()
            ).status()
        ).isEqualTo(
            PurchaseOrderStatus.ORDERED
        );
    }

    private SupplierCommand supplierCommand(
        String name
    ) {

        String suffix =
            suffix();

        return new SupplierCommand(
            name + " " + suffix,
            "+212600000000",
            "supplier-" + suffix + "@sup2i.test",
            "Supplier address"
        );
    }

    private PurchaseOrderCommand purchaseOrderCommand(
        String quantity,
        MeasurementUnit unit
    ) {

        return new PurchaseOrderCommand(
            supplierId,
            campusId,
            "PO-" + suffix(),
            new BigDecimal("100.00"),
            List.of(
                new PurchaseOrderItemCommand(
                    stockItemId,
                    new BigDecimal(
                        quantity
                    ),
                    unit,
                    new BigDecimal(
                        "2.50"
                    )
                )
            )
        );
    }

    private PurchaseOrderResponse createOrderedOrder(
        String quantity
    ) {

        UUID purchaseOrderId =
            UUID.randomUUID();

        purchaseOrderService.create(
            actorId,
            purchaseOrderId,
            purchaseOrderCommand(
                quantity,
                MeasurementUnit.GRAM
            )
        );

        purchaseOrderService.submit(
            actorId,
            purchaseOrderId
        );

        purchaseOrderService.approve(
            actorId,
            purchaseOrderId
        );

        return purchaseOrderService.markOrdered(
            actorId,
            purchaseOrderId
        );
    }

    private PurchaseOrderReceiptCommand receiptCommand(
        UUID receiptId,
        UUID lineId,
        UUID purchaseOrderItemId,
        String quantity
    ) {

        return new PurchaseOrderReceiptCommand(
            receiptId,
            stockLocationId,
            "RECEIPT-" + receiptId,
            "Procurement E2E receipt",
            List.of(
                new PurchaseOrderReceiptLineCommand(
                    lineId,
                    purchaseOrderItemId,
                    new BigDecimal(
                        quantity
                    ),
                    new BigDecimal(
                        "2.50"
                    ),
                    "LOT-" + lineId,
                    null
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
            INSERT INTO organizations(
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
        UUID ownerOrganizationId,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            suffix();

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
            id,
            ownerOrganizationId,
            "procurement-"
                + suffix
                + "@sup2i.test",
            prefix,
            "User"
        );

        return id;
    }

    private UUID insertCampus(
        UUID ownerOrganizationId,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            suffix();

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
            id,
            ownerOrganizationId,
            prefix + " Campus",
            prefix + suffix
        );

        return id;
    }

    private UUID insertLocation(
        UUID ownerCampusId,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            suffix();

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
            VALUES (?, ?, ?, ?, 'STORAGE', TRUE)
            """,
            id,
            ownerCampusId,
            prefix + " Location",
            prefix + suffix
        );

        return id;
    }

    private UUID insertStockLocation(
        UUID ownerLocationId
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_locations(
                id,
                location_id,
                name,
                type,
                is_active
            )
            VALUES (?, ?, ?, 'STORAGE', TRUE)
            """,
            id,
            ownerLocationId,
            "Procurement Storage " + suffix()
        );

        return id;
    }

    private UUID insertIngredient(
        UUID ownerOrganizationId
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO ingredients(
                id,
                organization_id,
                code,
                name,
                base_unit,
                is_active,
                track_stock
            )
            VALUES (?, ?, ?, ?, 'GRAM', TRUE, TRUE)
            """,
            id,
            ownerOrganizationId,
            "PROC-ING-" + suffix,
            "Procurement Ingredient " + suffix
        );

        return id;
    }

    private UUID insertIngredientStockItem(
        UUID ownerOrganizationId,
        UUID ownerIngredientId
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_items(
                id,
                organization_id,
                ingredient_id,
                base_unit,
                low_stock_threshold,
                track_expiry
            )
            VALUES (?, ?, ?, 'GRAM', 0.000, FALSE)
            """,
            id,
            ownerOrganizationId,
            ownerIngredientId
        );

        return id;
    }

    private BigDecimal physicalQuantity() {

        return jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(
                (
                    SELECT physical_quantity
                    FROM stock_balances
                    WHERE stock_item_id = ?
                      AND stock_location_id = ?
                ),
                0
            )
            """,
            BigDecimal.class,
            stockItemId,
            stockLocationId
        );
    }

    private Long stockReceiptCount(
        UUID receiptId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM stock_receipts
            WHERE id = ?
            """,
            Long.class,
            receiptId
        );
    }

    private UUID receiptPurchaseOrderId(
        UUID receiptId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT purchase_order_id
            FROM stock_receipts
            WHERE id = ?
            """,
            UUID.class,
            receiptId
        );
    }

    private UUID receiptLinePurchaseOrderItemId(
        UUID lineId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT purchase_order_item_id
            FROM stock_receipt_lines
            WHERE id = ?
            """,
            UUID.class,
            lineId
        );
    }

    private Long receiptMovementCount(
        UUID receiptId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_movements
            WHERE movement_type = 'PURCHASE_IN'
              AND reference_type = 'STOCK_RECEIPT'
              AND reference_id = ?
            """,
            Long.class,
            receiptId
        );
    }

    private Long totalPurchaseInMovements(
        UUID purchaseOrderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_movements im
            JOIN stock_receipt_lines srl
              ON srl.inventory_movement_id = im.id
            JOIN stock_receipts sr
              ON sr.id = srl.stock_receipt_id
            WHERE sr.purchase_order_id = ?
              AND im.movement_type = 'PURCHASE_IN'
            """,
            Long.class,
            purchaseOrderId
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
                12
            );
    }
}