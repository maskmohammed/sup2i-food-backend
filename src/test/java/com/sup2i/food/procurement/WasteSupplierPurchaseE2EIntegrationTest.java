package com.sup2i.food.procurement;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.service.AuthenticationTokens;
import com.sup2i.food.security.service.RefreshTokenService;

import org.junit.jupiter.api.Assertions;
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
import org.springframework.test.web.servlet.ResultActions;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class WasteSupplierPurchaseE2EIntegrationTest {

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
    private UUID campusId;
    private UUID locationId;
    private UUID ingredientId;
    private UUID stockItemId;
    private UUID stockLocationId;

    private Actor systemAdmin;
    private Actor kitchenStaff;
    private Actor snackManager;
    private Actor direction;
    private Actor student;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "WPS"
            );

        campusId =
            insertCampus(
                organizationId,
                "MAIN",
                true
            );

        locationId =
            insertLocation(
                campusId,
                "SNACK",
                "SNACK",
                true
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

        stockLocationId =
            insertStockLocation(
                locationId,
                "WPS",
                true
            );

        systemAdmin =
            insertRoleActor(
                organizationId,
                "WPS-ADMIN",
                "SYSTEM_ADMIN"
            );

        kitchenStaff =
            insertRoleActor(
                organizationId,
                "WPS-KITCHEN",
                "KITCHEN_STAFF"
            );

        snackManager =
            insertRoleActor(
                organizationId,
                "WPS-MGR",
                "SNACK_MANAGER"
            );

        direction =
            insertRoleActor(
                organizationId,
                "WPS-DIR",
                "DIRECTION"
            );

        student =
            insertStudentActor(
                organizationId,
                campusId,
                "WPS-STU"
            );
    }

    // =========================================================
    // 01 - SUPPLIERS : CRUD, STATUS, CONTRACTS, RBAC
    // =========================================================

    @Test
    void supplierDirectoryContractsAndRbac() throws Exception {

        String supplierJson =
            createSupplier(
                systemAdmin,
                supplierBody(
                    "E2E Distrib",
                    "0612345678",
                    "e2e-distrib@sup2i.test",
                    "Casablanca",
                    "Karim"
                )
            )
                .andExpect(
                    status().isCreated()
                )
                .andExpect(
                    jsonPath("$.name")
                        .value("E2E Distrib")
                )
                .andExpect(
                    jsonPath("$.status")
                        .value("ACTIVE")
                )
                .andExpect(
                    jsonPath("$.active")
                        .value(true)
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID supplierId =
            idOf(supplierJson);

        mockMvc.perform(
                get("/api/v1/admin/suppliers")
                    .header(
                        "Authorization",
                        bearer(direction)
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
                jsonPath("$[0].name")
                    .value("E2E Distrib")
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/suppliers/{supplierId}",
                    supplierId
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("ACTIVE")
            );

        createSupplier(
            direction,
            supplierBody(
                "Refus direction",
                null,
                null,
                null,
                null
            )
        )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                get("/api/v1/admin/suppliers")
                    .header(
                        "Authorization",
                        bearer(kitchenStaff)
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/suppliers/{supplierId}",
                    supplierId
                )
                    .header(
                        "Authorization",
                        bearer(systemAdmin)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        supplierBody(
                            "E2E Distribution SAS",
                            "0611111111",
                            "e2e-sas@sup2i.test",
                            "Rabat",
                            "Salma"
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.name")
                    .value("E2E Distribution SAS")
            )
            .andExpect(
                jsonPath("$.contact")
                    .value("Salma")
            );

        mockMvc.perform(
                patch(
                    "/api/v1/admin/suppliers/{supplierId}/status",
                    supplierId
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
                        { "status": "INACTIVE" }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("INACTIVE")
            )
            .andExpect(
                jsonPath("$.active")
                    .value(false)
            );

        mockMvc.perform(
                patch(
                    "/api/v1/admin/suppliers/{supplierId}/status",
                    supplierId
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
                        { "status": "ACTIVE" }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.active")
                    .value(true)
            );

        String contractJson =
            createContract(
                systemAdmin,
                contractBody(
                    supplierId,
                    ingredientId
                )
            )
                .andExpect(
                    status().isCreated()
                )
                .andExpect(
                    jsonPath("$.status")
                        .value("ACTIVE")
                )
                .andExpect(
                    jsonPath("$.supplierId")
                        .value(supplierId.toString())
                )
                .andExpect(
                    jsonPath("$.ingredientId")
                        .value(ingredientId.toString())
                )
                .andExpect(
                    jsonPath("$.unitPrice")
                        .value(12.50)
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID contractId =
            idOf(contractJson);

        mockMvc.perform(
                get("/api/v1/admin/supplier-contracts")
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(1)
            );

        createContract(
            direction,
            contractBody(
                supplierId,
                ingredientId
            )
        )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                patch(
                    "/api/v1/admin/supplier-contracts/{contractId}/status",
                    contractId
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
                        { "status": "EXPIRED" }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("EXPIRED")
            );

        mockMvc.perform(
                patch(
                    "/api/v1/admin/supplier-contracts/{contractId}/status",
                    contractId
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
                        { "status": "ACTIVE" }
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

    // =========================================================
    // 02 - PURCHASE ORDER : SEND -> CONFIRM -> RECEIVE -> WASTE
    // =========================================================

    @Test
    void purchaseOrderReceiveStockAndWasteFlow() throws Exception {

        String supplierJson =
            createSupplier(
                systemAdmin,
                supplierBody(
                    "E2E Marché",
                    "0622222222",
                    "e2e-marche@sup2i.test",
                    "Marrakech",
                    "Ali"
                )
            )
                .andExpect(
                    status().isCreated()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID supplierId =
            idOf(supplierJson);

        String purchaseOrderJson =
            createPurchaseOrder(
                systemAdmin,
                purchaseOrderBody(
                    supplierId,
                    ingredientId
                )
            )
                .andExpect(
                    status().isCreated()
                )
                .andExpect(
                    jsonPath("$.status")
                        .value("DRAFT")
                )
                .andExpect(
                    jsonPath("$.supplierId")
                        .value(supplierId.toString())
                )
                .andExpect(
                    jsonPath("$.campusId")
                        .value(campusId.toString())
                )
                .andExpect(
                    jsonPath("$.totalEstimated")
                        .value(125.00)
                )
                .andExpect(
                    jsonPath("$.lines.length()")
                        .value(1)
                )
                .andExpect(
                    jsonPath("$.lines[0].ingredientId")
                        .value(ingredientId.toString())
                )
                .andExpect(
                    jsonPath("$.lines[0].quantity")
                        .value(10.000)
                )
                .andExpect(
                    jsonPath("$.lines[0].unit")
                        .value("GRAM")
                )
                .andExpect(
                    jsonPath("$.lines[0].lineTotal")
                        .value(125.00)
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID purchaseOrderId =
            idOf(purchaseOrderJson);

        String reference =
            field(purchaseOrderJson, "reference");

        Assertions.assertTrue(
            reference.startsWith("PO-"),
            "Purchase order reference should start with PO-"
        );

        UUID lineId =
            UUID.fromString(
                field(purchaseOrderJson, "lines[0].id")
            );

        mockMvc.perform(
                get("/api/v1/admin/purchase-orders")
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(1)
            );

        createPurchaseOrder(
            direction,
            purchaseOrderBody(
                supplierId,
                ingredientId
            )
        )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/purchase-orders/{purchaseOrderId}/send",
                    purchaseOrderId
                )
                    .header(
                        "Authorization",
                        bearer(systemAdmin)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("SENT")
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/purchase-orders/{purchaseOrderId}/receive",
                    purchaseOrderId
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        receiveBody(
                            stockLocationId,
                            lineId,
                            "10.000",
                            "12.5000",
                            "LOT-E2E",
                            iso(
                                OffsetDateTime.now()
                                    .plusDays(45)
                            )
                        )
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/purchase-orders/{purchaseOrderId}/confirm",
                    purchaseOrderId
                )
                    .header(
                        "Authorization",
                        bearer(systemAdmin)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("CONFIRMED")
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/purchase-orders/{purchaseOrderId}/receive",
                    purchaseOrderId
                )
                    .header(
                        "Authorization",
                        bearer(systemAdmin)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        receiveBody(
                            stockLocationId,
                            lineId,
                            "10.000",
                            "12.5000",
                            "LOT-E2E",
                            iso(
                                OffsetDateTime.now()
                                    .plusDays(45)
                            )
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("RECEIVED")
            )
            .andExpect(
                jsonPath("$.lines[0].receivedQuantity")
                    .value(10.000)
            )
            .andExpect(
                jsonPath("$.lines[0].remainingQuantity")
                    .value(0.000)
            )
            .andExpect(
                jsonPath("$.receipts.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.receipts[0].lines[0].generatedLotId")
                    .isNotEmpty()
            )
            .andExpect(
                jsonPath("$.receipts[0].lines[0].inventoryMovementId")
                    .isNotEmpty()
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "10.000"
        );

        Assertions.assertEquals(
            1L,
            movementCount(stockItemId, "PURCHASE_IN"),
            "Expected one PURCHASE_IN movement"
        );

        Assertions.assertEquals(
            1L,
            lotCount(),
            "Expected one generated stock lot"
        );

        mockMvc.perform(
                get(
                    "/api/v1/admin/purchase-orders/{purchaseOrderId}",
                    purchaseOrderId
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.history.length()")
                    .value(4)
            )
            .andExpect(
                jsonPath("$.history[0].eventType")
                    .value("RECEIVED")
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/purchase-orders/{purchaseOrderId}/cancel",
                    purchaseOrderId
                )
                    .header(
                        "Authorization",
                        bearer(systemAdmin)
                    )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );

        String wasteJson =
            recordWaste(
                kitchenStaff,
                wasteBody(
                    ingredientId,
                    stockLocationId,
                    "4.000"
                )
            )
                .andExpect(
                    status().isCreated()
                )
                .andExpect(
                    jsonPath("$.wasteType")
                        .value("EXPIRED")
                )
                .andExpect(
                    jsonPath("$.ingredientId")
                        .value(ingredientId.toString())
                )
                .andExpect(
                    jsonPath("$.quantity")
                        .value(4.000)
                )
                .andExpect(
                    jsonPath("$.estimatedCost")
                        .value(50.00)
                )
                .andExpect(
                    jsonPath("$.inventoryMovementId")
                        .isNotEmpty()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID wasteId =
            idOf(wasteJson);

        assertBalance(
            stockItemId,
            stockLocationId,
            "6.000"
        );

        mockMvc.perform(
                get("/api/v1/waste/stats")
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.recordCount")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.totalQuantity")
                    .value(4.000)
            )
            .andExpect(
                jsonPath("$.totalCost")
                    .value(50.00)
            )
            .andExpect(
                jsonPath("$.byType.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.byType[0].wasteType")
                    .value("EXPIRED")
            )
            .andExpect(
                jsonPath("$.byType[0].totalQuantity")
                    .value(4.000)
            );

        mockMvc.perform(
                get(
                    "/api/v1/waste/{recordId}",
                    wasteId
                )
                    .header(
                        "Authorization",
                        bearer(kitchenStaff)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.estimatedCost")
                    .value(50.00)
            );

        recordWaste(
            direction,
            wasteBody(
                ingredientId,
                stockLocationId,
                "1.000"
            )
        )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                get("/api/v1/waste/stats")
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        recordWaste(
            snackManager,
            wasteBody(
                ingredientId,
                stockLocationId,
                "1.000"
            )
        )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.estimatedCost")
                    .value(12.50)
            );

        mockMvc.perform(
                get("/api/v1/waste")
                    .header(
                        "Authorization",
                        bearer(kitchenStaff)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(2)
            );

        mockMvc.perform(
                get("/api/v1/waste/stats")
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.recordCount")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.totalQuantity")
                    .value(5.000)
            )
            .andExpect(
                jsonPath("$.totalCost")
                    .value(62.50)
            )
            .andExpect(
                jsonPath("$.byType[0].totalQuantity")
                    .value(5.000)
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "5.000"
        );

        recordWaste(
            systemAdmin,
            wasteBody(
                ingredientId,
                stockLocationId,
                "6.000"
            )
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "5.000"
        );
    }

    // =========================================================
    // 03 - PURCHASE ORDER : PARTIAL RECEIPT then FINAL RECEIPT
    // =========================================================

    @Test
    void partialReceiptThenFinalReceipt() throws Exception {

        String supplierJson =
            createSupplier(
                systemAdmin,
                supplierBody(
                    "E2E Fournisseur",
                    "0633333333",
                    "e2e-four@sup2i.test",
                    "Agadir",
                    "Yassine"
                )
            )
                .andExpect(
                    status().isCreated()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID supplierId =
            idOf(supplierJson);

        String purchaseOrderJson =
            createPurchaseOrder(
                systemAdmin,
                purchaseOrderBody(
                    supplierId,
                    ingredientId
                )
            )
                .andExpect(
                    status().isCreated()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID purchaseOrderId =
            idOf(purchaseOrderJson);

        UUID lineId =
            UUID.fromString(
                field(purchaseOrderJson, "lines[0].id")
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/purchase-orders/{purchaseOrderId}/send",
                    purchaseOrderId
                )
                    .header(
                        "Authorization",
                        bearer(systemAdmin)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/purchase-orders/{purchaseOrderId}/confirm",
                    purchaseOrderId
                )
                    .header(
                        "Authorization",
                        bearer(systemAdmin)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/purchase-orders/{purchaseOrderId}/receive",
                    purchaseOrderId
                )
                    .header(
                        "Authorization",
                        bearer(systemAdmin)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        receiveBody(
                            stockLocationId,
                            lineId,
                            "6.000",
                            "12.5000",
                            null,
                            null
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("PARTIALLY_RECEIVED")
            )
            .andExpect(
                jsonPath("$.lines[0].receivedQuantity")
                    .value(6.000)
            )
            .andExpect(
                jsonPath("$.lines[0].remainingQuantity")
                    .value(4.000)
            )
            .andExpect(
                jsonPath("$.receipts.length()")
                    .value(1)
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "6.000"
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/purchase-orders/{purchaseOrderId}/receive",
                    purchaseOrderId
                )
                    .header(
                        "Authorization",
                        bearer(systemAdmin)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        receiveBody(
                            stockLocationId,
                            lineId,
                            "4.000",
                            "12.5000",
                            null,
                            null
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("RECEIVED")
            )
            .andExpect(
                jsonPath("$.lines[0].remainingQuantity")
                    .value(0.000)
            )
            .andExpect(
                jsonPath("$.receipts.length()")
                    .value(2)
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "10.000"
        );

        mockMvc.perform(
                get(
                    "/api/v1/admin/purchase-orders/{purchaseOrderId}/history",
                    purchaseOrderId
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(5)
            );

        Assertions.assertEquals(
            2L,
            movementCount(stockItemId, "PURCHASE_IN"),
            "Expected two PURCHASE_IN movements"
        );
    }

    // =========================================================
    // HTTP HELPERS
    // =========================================================

    private ResultActions createSupplier(
        Actor actor,
        String body
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/admin/suppliers")
                .header(
                    "Authorization",
                    bearer(actor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(body)
        );
    }

    private ResultActions createContract(
        Actor actor,
        String body
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/admin/supplier-contracts")
                .header(
                    "Authorization",
                    bearer(actor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(body)
        );
    }

    private ResultActions createPurchaseOrder(
        Actor actor,
        String body
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/admin/purchase-orders")
                .header(
                    "Authorization",
                    bearer(actor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(body)
        );
    }

    private ResultActions recordWaste(
        Actor actor,
        String body
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/waste")
                .header(
                    "Authorization",
                    bearer(actor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(body)
        );
    }

    // =========================================================
    // REQUEST BODIES
    // =========================================================

    private String supplierBody(
        String name,
        String phone,
        String email,
        String address,
        String contact
    ) {

        return """
            {
              "name": %s,
              "phone": %s,
              "email": %s,
              "address": %s,
              "contact": %s
            }
            """.formatted(
                textOrNull(name),
                textOrNull(phone),
                textOrNull(email),
                textOrNull(address),
                textOrNull(contact)
            );
    }

    private String contractBody(
        UUID supplierId,
        UUID subjectIngredientId
    ) {

        return """
            {
              "supplierId": "%s",
              "ingredientId": "%s",
              "unitPrice": "12.50",
              "unit": "GRAM",
              "minQuantity": "5.000",
              "paymentTerms": "30j",
              "leadTimeDays": 3,
              "startDate": null,
              "endDate": null,
              "notes": "Contrat E2E"
            }
            """.formatted(
                supplierId,
                subjectIngredientId
            );
    }

    private String purchaseOrderBody(
        UUID supplierId,
        UUID orderedIngredientId
    ) {

        return """
            {
              "supplierId": "%s",
              "campusId": "%s",
              "notes": "Commande E2E",
              "lines": [
                {
                  "ingredientId": "%s",
                  "quantity": "10.000",
                  "unit": "GRAM",
                  "unitPrice": "12.50"
                }
              ]
            }
            """.formatted(
                supplierId,
                campusId,
                orderedIngredientId
            );
    }

    private String receiveBody(
        UUID selectedStockLocationId,
        UUID purchaseOrderLineId,
        String quantity,
        String unitCost,
        String lotNumber,
        String expiresAt
    ) {

        return """
            {
              "stockLocationId": "%s",
              "notes": "Réception E2E",
              "items": [
                {
                  "purchaseOrderLineId": "%s",
                  "quantity": "%s",
                  "unitCost": "%s",
                  "lotNumber": %s,
                  "expiresAt": %s
                }
              ]
            }
            """.formatted(
                selectedStockLocationId,
                purchaseOrderLineId,
                quantity,
                unitCost,
                textOrNull(lotNumber),
                textOrNull(expiresAt)
            );
    }

    private String wasteBody(
        UUID wastedIngredientId,
        UUID selectedStockLocationId,
        String quantity
    ) {

        return """
            {
              "ingredientId": "%s",
              "campusId": "%s",
              "stockLocationId": "%s",
              "wasteType": "EXPIRED",
              "quantity": "%s",
              "unit": "GRAM",
              "reasonText": "Produits périmés constatés",
              "photoUrl": "https://cdn.sup2i.test/waste-e2e.jpg"
            }
            """.formatted(
                wastedIngredientId,
                campusId,
                selectedStockLocationId,
                quantity
            );
    }

    // =========================================================
    // TENANT / INVENTORY FIXTURES
    // =========================================================

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
            prefix + suffix()
        );

        return id;
    }

    private UUID insertCampus(
        UUID tenantId,
        String prefix,
        boolean active
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
            VALUES (?, ?, ?, ?, ?)
            """,
            id,
            tenantId,
            prefix + " Campus",
            "C" + suffix(),
            active
        );

        return id;
    }

    private UUID insertLocation(
        UUID selectedCampusId,
        String prefix,
        String type,
        boolean active
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
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            id,
            selectedCampusId,
            prefix + " Location",
            "L" + suffix(),
            type,
            active
        );

        return id;
    }

    private UUID insertIngredient(
        UUID tenantId
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
            VALUES (?, ?, ?, ?, 'GRAM', TRUE)
            """,
            id,
            tenantId,
            "ING-" + suffix(),
            "Ingrédient E2E"
        );

        return id;
    }

    private UUID insertIngredientStockItem(
        UUID tenantId,
        UUID selectedIngredientId
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
                track_expiry
            )
            VALUES (?, ?, ?, 'GRAM', FALSE)
            """,
            id,
            tenantId,
            selectedIngredientId
        );

        return id;
    }

    private UUID insertStockLocation(
        UUID selectedLocationId,
        String prefix,
        boolean active
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
            VALUES (?, ?, ?, 'STORAGE', ?)
            """,
            id,
            selectedLocationId,
            prefix + " Stock",
            active
        );

        return id;
    }

    private Actor insertStudentActor(
        UUID tenantId,
        UUID selectedCampusId,
        String prefix
    ) {

        UUID userId =
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
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """,
            userId,
            tenantId,
            "wps-"
                + prefix.toLowerCase()
                + "-"
                + suffix
                + "@sup2i.test",
            "Waste",
            prefix
        );

        UUID studentId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO students (
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
            selectedCampusId,
            "STU-" + suffix
        );

        return new Actor(
            userId,
            studentId,
            token(userId)
        );
    }

    private Actor insertRoleActor(
        UUID tenantId,
        String prefix,
        String roleCode
    ) {

        UUID userId =
            insertUser(
                tenantId,
                prefix
            );

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

        return new Actor(
            userId,
            null,
            token(userId)
        );
    }

    private UUID insertUser(
        UUID tenantId,
        String prefix
    ) {

        UUID userId =
            UUID.randomUUID();

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
            "wps-"
                + prefix.toLowerCase()
                + "-"
                + suffix()
                + "@sup2i.test",
            "Waste",
            prefix
        );

        return userId;
    }

    // =========================================================
    // DATA ASSERTIONS
    // =========================================================

    private void assertBalance(
        UUID selectedStockItemId,
        UUID selectedStockLocationId,
        String expected
    ) {

        BigDecimal actual =
            jdbcTemplate.queryForObject(
                """
                SELECT physical_quantity
                FROM stock_balances
                WHERE stock_item_id = ? AND stock_location_id = ?
                """,
                BigDecimal.class,
                selectedStockItemId,
                selectedStockLocationId
            );

        Assertions.assertEquals(
            0,
            new BigDecimal(expected)
                .compareTo(actual),
            "Physical balance mismatch: expected "
                + expected
                + " but was "
                + actual
        );
    }

    private long movementCount(
        UUID selectedStockItemId,
        String movementType
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM inventory_movements
                WHERE stock_item_id = ? AND movement_type = ?
                """,
                Long.class,
                selectedStockItemId,
                movementType
            );

        return count == null
            ? 0L
            : count;
    }

    private long lotCount() {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_lots
                """,
                Long.class
            );

        return count == null
            ? 0L
            : count;
    }

    // =========================================================
    // IDENTITY HELPERS
    // =========================================================

    private String token(
        UUID userId
    ) {

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "wps-e2e-"
                    + suffix(),
                InetAddress
                    .getLoopbackAddress()
            );

        return tokens.accessToken();
    }

    private String bearer(
        Actor requestActor
    ) {

        return "Bearer "
            + requestActor.token();
    }

    // =========================================================
    // JSON HELPERS
    // =========================================================

    private UUID idOf(
        String json
    ) {

        return UUID.fromString(
            field(json, "id")
        );
    }

    private String field(
        String json,
        String path
    ) {

        try {

            com.fasterxml.jackson.databind.JsonNode node =
                JSON.readTree(json);

            for (
                String segment
                : tokenize(path)
            ) {

                if (node == null) {
                    throw new IllegalStateException(
                        "Field "
                            + path
                            + " not found in: "
                            + json
                    );
                }

                if (segment.endsWith("]")) {
                    String name =
                        segment.substring(
                            0,
                            segment.indexOf('[')
                        );
                    int index =
                        Integer.parseInt(
                            segment.substring(
                                segment.indexOf('[') + 1,
                                segment.length() - 1
                            )
                        );
                    node =
                        node.get(name)
                            .get(index);
                } else {
                    node =
                        node.get(segment);
                }
            }

            if (node == null) {
                throw new IllegalStateException(
                    "Field "
                        + path
                        + " not found in: "
                        + json
                );
            }

            return node.asText();

        } catch (
            java.io.IOException exception
        ) {
            throw new IllegalStateException(
                "Could not parse JSON: "
                    + json,
                exception
            );
        }
    }

    private static java.util.List<String> tokenize(
        String path
    ) {

        return java.util.Arrays.stream(
                path.split("\\.")
            )
            .toList();
    }

    private String textOrNull(
        String value
    ) {

        return value == null
            ? "null"
            : "\"" + value + "\"";
    }

    private String iso(
        OffsetDateTime dateTime
    ) {

        return dateTime.format(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
        );
    }

    private String suffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);
    }

    private record Actor(
        UUID userId,
        UUID studentId,
        String token
    ) {
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper
        JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();
}