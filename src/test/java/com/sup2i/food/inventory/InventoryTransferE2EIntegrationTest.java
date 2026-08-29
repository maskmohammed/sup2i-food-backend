package com.sup2i.food.inventory;

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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class InventoryTransferE2EIntegrationTest {

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
    private String authSessionId;

    @BeforeEach
    void seedTenantAndSession() {

        String suffix =
            randomSuffix();

        organizationId =
            UUID.randomUUID();

        userId =
            UUID.randomUUID();

        email =
            "inventory-transfer-"
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
            "Inventory Transfer " + suffix,
            "IT" + suffix
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
            "Inventory",
            "Transfer"
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "inventory-transfer-e2e",
                InetAddress.getLoopbackAddress()
            );

        Jwt jwt =
            jwtDecoder.decode(
                tokens.accessToken()
            );

        authSessionId =
            jwt.getClaimAsString("sid");
    }

    @Test
    void transferRequiresProductWritePermission()
        throws Exception {

        UUID source =
            UUID.randomUUID();

        UUID destination =
            UUID.randomUUID();

        UUID stockItem =
            UUID.randomUUID();

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/transfers/{transferId}",
                    UUID.randomUUID()
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        transferBody(
                            source,
                            destination,
                            UUID.randomUUID(),
                            stockItem,
                            "1.000"
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
    void draftIsEditableIdempotentAndFrozenAfterApproval()
        throws Exception {

        UUID source =
            insertOwnedStockLocation(
                "DRAFT-SOURCE",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "DRAFT-DEST",
                true
            );

        UUID stockItem =
            insertOwnedStockItem(
                "DRAFT-ITEM",
                false
            );

        insertBalance(
            stockItem,
            source,
            "10.000",
            "0.000"
        );

        UUID transferId =
            UUID.randomUUID();

        UUID lineId =
            UUID.randomUUID();

        String initial =
            transferBody(
                source,
                destination,
                lineId,
                stockItem,
                "2.000"
            );

        upsert(
            transferId,
            initial
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.transfer.status")
                    .value("DRAFT")
            );

        upsert(
            transferId,
            initial
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        String edited =
            transferBody(
                source,
                destination,
                lineId,
                stockItem,
                "3.000"
            );

        upsert(
            transferId,
            edited
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        assertThat(
            transferLineQuantity(
                lineId
            )
        ).isEqualByComparingTo("3.000");

        approve(transferId)
            .andExpect(status().isOk());

        upsert(
            transferId,
            transferBody(
                source,
                destination,
                lineId,
                stockItem,
                "4.000"
            )
        )
            .andExpect(
                status().isConflict()
            );

        assertThat(
            transferLineQuantity(
                lineId
            )
        ).isEqualByComparingTo("3.000");
    }

    @Test
    void requestValidationRejectsInvalidLocationsAndDuplicateLines()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "VALIDATION",
                true
            );

        UUID itemOne =
            insertOwnedStockItem(
                "VALIDATION-A",
                false
            );

        UUID itemTwo =
            insertOwnedStockItem(
                "VALIDATION-B",
                false
            );

        upsert(
            UUID.randomUUID(),
            transferBody(
                location,
                location,
                UUID.randomUUID(),
                itemOne,
                "1.000"
            )
        )
            .andExpect(
                status().isBadRequest()
            );

        UUID source =
            insertOwnedStockLocation(
                "VALIDATION-S",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "VALIDATION-D",
                true
            );

        UUID duplicateLineId =
            UUID.randomUUID();

        String duplicateLines =
            """
            {
              "sourceStockLocationId": "%s",
              "destinationStockLocationId": "%s",
              "lines": [
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 1.000,
                  "unit": "PIECE"
                },
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 1.000,
                  "unit": "PIECE"
                }
              ]
            }
            """.formatted(
                source,
                destination,
                duplicateLineId,
                itemOne,
                duplicateLineId,
                itemTwo
            );

        upsert(
            UUID.randomUUID(),
            duplicateLines
        )
            .andExpect(
                status().isBadRequest()
            );

        String duplicateItem =
            """
            {
              "sourceStockLocationId": "%s",
              "destinationStockLocationId": "%s",
              "lines": [
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 1.000,
                  "unit": "PIECE"
                },
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 2.000,
                  "unit": "PIECE"
                }
              ]
            }
            """.formatted(
                source,
                destination,
                UUID.randomUUID(),
                itemOne,
                UUID.randomUUID(),
                itemOne
            );

        upsert(
            UUID.randomUUID(),
            duplicateItem
        )
            .andExpect(
                status().isBadRequest()
            );
    }

    @Test
    void tenantIsolationProtectsCreationAndReading()
        throws Exception {

        UUID ownedSource =
            insertOwnedStockLocation(
                "TENANT-S",
                true
            );

        UUID ownedDestination =
            insertOwnedStockLocation(
                "TENANT-D",
                true
            );

        UUID ownedItem =
            insertOwnedStockItem(
                "TENANT-ITEM",
                false
            );

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-TRANSFER"
            );

        UUID foreignLocation =
            insertStockLocationForOrganization(
                foreignOrganization,
                "FOREIGN-LOCATION",
                true
            );

        upsert(
            UUID.randomUUID(),
            transferBody(
                foreignLocation,
                ownedDestination,
                UUID.randomUUID(),
                ownedItem,
                "1.000"
            )
        )
            .andExpect(
                status().isNotFound()
            );

        UUID foreignItem =
            insertStockItemForOrganization(
                foreignOrganization,
                "FOREIGN-ITEM",
                false
            );

        upsert(
            UUID.randomUUID(),
            transferBody(
                ownedSource,
                ownedDestination,
                UUID.randomUUID(),
                foreignItem,
                "1.000"
            )
        )
            .andExpect(
                status().isNotFound()
            );

        UUID foreignUser =
            insertUser(
                foreignOrganization
            );

        UUID foreignSource =
            insertStockLocationForOrganization(
                foreignOrganization,
                "FOREIGN-S",
                true
            );

        UUID foreignDestination =
            insertStockLocationForOrganization(
                foreignOrganization,
                "FOREIGN-D",
                true
            );

        UUID foreignTransfer =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_transfers (
                id,
                source_stock_location_id,
                destination_stock_location_id,
                status,
                requested_by,
                requested_at
            )
            VALUES (?, ?, ?, 'DRAFT', ?, CURRENT_TIMESTAMP)
            """,
            foreignTransfer,
            foreignSource,
            foreignDestination,
            foreignUser
        );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/transfers/{transferId}",
                    foreignTransfer
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
            )
            .andExpect(
                status().isNotFound()
            );
    }

    @Test
    void approvalChecksAvailabilityAndIsIdempotent()
        throws Exception {

        UUID source =
            insertOwnedStockLocation(
                "APPROVE-S",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "APPROVE-D",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "APPROVE-ITEM",
                false
            );

        insertBalance(
            item,
            source,
            "10.000",
            "3.000"
        );

        UUID okTransfer =
            UUID.randomUUID();

        upsert(
            okTransfer,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "7.000"
            )
        );

        approve(okTransfer)
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.transfer.status")
                    .value("APPROVED")
            );

        approve(okTransfer)
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        UUID insufficientTransfer =
            UUID.randomUUID();

        upsert(
            insufficientTransfer,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "8.000"
            )
        );

        approve(insufficientTransfer)
            .andExpect(
                status().isConflict()
            );

        assertThat(
            transferStatus(
                insufficientTransfer
            )
        ).isEqualTo("DRAFT");
    }

    @Test
    void dispatchUsesAvailableStockAndIsIdempotent()
        throws Exception {

        UUID source =
            insertOwnedStockLocation(
                "DISPATCH-S",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "DISPATCH-D",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "DISPATCH-ITEM",
                false
            );

        insertBalance(
            item,
            source,
            "10.000",
            "3.000"
        );

        UUID transferId =
            UUID.randomUUID();

        upsert(
            transferId,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "6.000"
            )
        );

        approve(transferId);

        dispatch(transferId)
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.transfer.status")
                    .value("IN_TRANSIT")
            );

        assertBalance(
            item,
            source,
            "4.000",
            "3.000"
        );

        assertThat(
            movementCount(
                transferId,
                "TRANSFER_OUT"
            )
        ).isEqualTo(1L);

        dispatch(transferId)
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        assertThat(
            movementCount(
                transferId,
                "TRANSFER_OUT"
            )
        ).isEqualTo(1L);
    }

    @Test
    void dispatchRevalidatesStockAfterApproval()
        throws Exception {

        UUID source =
            insertOwnedStockLocation(
                "REVALIDATE-S",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "REVALIDATE-D",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "REVALIDATE",
                false
            );

        insertBalance(
            item,
            source,
            "10.000",
            "0.000"
        );

        UUID transferId =
            UUID.randomUUID();

        upsert(
            transferId,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "8.000"
            )
        );

        approve(transferId)
            .andExpect(status().isOk());

        jdbcTemplate.update(
            """
            UPDATE stock_balances
            SET physical_quantity = 5.000
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            item,
            source
        );

        dispatch(transferId)
            .andExpect(
                status().isConflict()
            );

        assertBalance(
            item,
            source,
            "5.000",
            "0.000"
        );

        assertThat(
            movementCount(
                transferId,
                "TRANSFER_OUT"
            )
        ).isZero();

        assertThat(
            transferStatus(
                transferId
            )
        ).isEqualTo("APPROVED");
    }

    @Test
    void multiLineDispatchRollsBackCompletely()
        throws Exception {

        UUID source =
            insertOwnedStockLocation(
                "ROLLBACK-S",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "ROLLBACK-D",
                true
            );

        UUID itemOne =
            insertOwnedStockItem(
                "ROLLBACK-A",
                false
            );

        UUID itemTwo =
            insertOwnedStockItem(
                "ROLLBACK-B",
                false
            );

        insertBalance(
            itemOne,
            source,
            "10.000",
            "0.000"
        );

        insertBalance(
            itemTwo,
            source,
            "10.000",
            "0.000"
        );

        UUID transferId =
            UUID.randomUUID();

        String body =
            """
            {
              "sourceStockLocationId": "%s",
              "destinationStockLocationId": "%s",
              "reason": "Rollback test",
              "lines": [
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 5.000,
                  "unit": "PIECE"
                },
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 5.000,
                  "unit": "PIECE"
                }
              ]
            }
            """.formatted(
                source,
                destination,
                UUID.randomUUID(),
                itemOne,
                UUID.randomUUID(),
                itemTwo
            );

        upsert(
            transferId,
            body
        );

        approve(transferId);

        jdbcTemplate.update(
            """
            UPDATE stock_balances
            SET physical_quantity = 2.000
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            itemTwo,
            source
        );

        dispatch(transferId)
            .andExpect(
                status().isConflict()
            );

        assertBalance(
            itemOne,
            source,
            "10.000",
            "0.000"
        );

        assertBalance(
            itemTwo,
            source,
            "2.000",
            "0.000"
        );

        assertThat(
            movementCount(
                transferId,
                "TRANSFER_OUT"
            )
        ).isZero();

        assertThat(
            transferStatus(
                transferId
            )
        ).isEqualTo("APPROVED");
    }

    @Test
    void activeSourceInventoryBlocksDispatch()
        throws Exception {

        UUID source =
            insertOwnedStockLocation(
                "COUNT-SOURCE",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "COUNT-DEST",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "COUNT-SOURCE-ITEM",
                false
            );

        insertBalance(
            item,
            source,
            "5.000",
            "0.000"
        );

        UUID transferId =
            UUID.randomUUID();

        upsert(
            transferId,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "2.000"
            )
        );

        approve(transferId);

        insertInventorySession(
            source,
            "OPEN"
        );

        dispatch(transferId)
            .andExpect(
                status().isConflict()
            );

        assertBalance(
            item,
            source,
            "5.000",
            "0.000"
        );

        assertThat(
            movementCount(
                transferId,
                "TRANSFER_OUT"
            )
        ).isZero();
    }

    @Test
    void receiveMovesStockToDestinationAndIsIdempotent()
        throws Exception {

        UUID source =
            insertOwnedStockLocation(
                "RECEIVE-S",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "RECEIVE-D",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "RECEIVE",
                false
            );

        insertBalance(
            item,
            source,
            "10.000",
            "0.000"
        );

        insertBalance(
            item,
            destination,
            "1.000",
            "0.000"
        );

        UUID transferId =
            UUID.randomUUID();

        upsert(
            transferId,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "4.000"
            )
        );

        approve(transferId);
        dispatch(transferId);

        receive(transferId)
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.transfer.status")
                    .value("RECEIVED")
            );

        assertBalance(
            item,
            source,
            "6.000",
            "0.000"
        );

        assertBalance(
            item,
            destination,
            "5.000",
            "0.000"
        );

        assertThat(
            movementCount(
                transferId,
                "TRANSFER_OUT"
            )
        ).isEqualTo(1L);

        assertThat(
            movementCount(
                transferId,
                "TRANSFER_IN"
            )
        ).isEqualTo(1L);

        receive(transferId)
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        assertThat(
            movementCount(
                transferId,
                "TRANSFER_IN"
            )
        ).isEqualTo(1L);
    }

    @Test
    void destinationInventoryAndInactiveLocationBlockReceive()
        throws Exception {

        UUID sourceOne =
            insertOwnedStockLocation(
                "DEST-GUARD-S1",
                true
            );

        UUID destinationOne =
            insertOwnedStockLocation(
                "DEST-GUARD-D1",
                true
            );

        UUID itemOne =
            insertOwnedStockItem(
                "DEST-GUARD-A",
                false
            );

        insertBalance(
            itemOne,
            sourceOne,
            "5.000",
            "0.000"
        );

        UUID transferOne =
            UUID.randomUUID();

        upsert(
            transferOne,
            transferBody(
                sourceOne,
                destinationOne,
                UUID.randomUUID(),
                itemOne,
                "2.000"
            )
        );

        approve(transferOne);
        dispatch(transferOne);

        insertInventorySession(
            destinationOne,
            "COUNTING"
        );

        receive(transferOne)
            .andExpect(
                status().isConflict()
            );

        assertThat(
            movementCount(
                transferOne,
                "TRANSFER_IN"
            )
        ).isZero();

        UUID sourceTwo =
            insertOwnedStockLocation(
                "DEST-GUARD-S2",
                true
            );

        UUID destinationTwo =
            insertOwnedStockLocation(
                "DEST-GUARD-D2",
                true
            );

        UUID itemTwo =
            insertOwnedStockItem(
                "DEST-GUARD-B",
                false
            );

        insertBalance(
            itemTwo,
            sourceTwo,
            "5.000",
            "0.000"
        );

        UUID transferTwo =
            UUID.randomUUID();

        upsert(
            transferTwo,
            transferBody(
                sourceTwo,
                destinationTwo,
                UUID.randomUUID(),
                itemTwo,
                "2.000"
            )
        );

        approve(transferTwo);
        dispatch(transferTwo);

        jdbcTemplate.update(
            """
            UPDATE stock_locations
            SET is_active = FALSE
            WHERE id = ?
            """,
            destinationTwo
        );

        receive(transferTwo)
            .andExpect(
                status().isBadRequest()
            );

        assertThat(
            movementCount(
                transferTwo,
                "TRANSFER_IN"
            )
        ).isZero();
    }

    @Test
    void cancellationRulesAndTerminalStatesAreEnforced()
        throws Exception {

        UUID source =
            insertOwnedStockLocation(
                "CANCEL-S",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "CANCEL-D",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "CANCEL",
                false
            );

        insertBalance(
            item,
            source,
            "20.000",
            "0.000"
        );

        UUID draftTransfer =
            UUID.randomUUID();

        upsert(
            draftTransfer,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "1.000"
            )
        );

        cancel(draftTransfer)
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        cancel(draftTransfer)
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        UUID approvedTransfer =
            UUID.randomUUID();

        upsert(
            approvedTransfer,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "1.000"
            )
        );

        approve(approvedTransfer);

        cancel(approvedTransfer)
            .andExpect(status().isOk());

        UUID dispatchedTransfer =
            UUID.randomUUID();

        upsert(
            dispatchedTransfer,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "2.000"
            )
        );

        approve(dispatchedTransfer);
        dispatch(dispatchedTransfer);

        cancel(dispatchedTransfer)
            .andExpect(
                status().isConflict()
            );

        receive(dispatchedTransfer)
            .andExpect(status().isOk());

        cancel(dispatchedTransfer)
            .andExpect(
                status().isConflict()
            );

        dispatch(dispatchedTransfer)
            .andExpect(
                status().isConflict()
            );

        assertThat(
            transferStatus(
                dispatchedTransfer
            )
        ).isEqualTo("RECEIVED");
    }

    @Test
    void expiryTrackedTransferUsesFefoAndPreservesLotMetadata()
        throws Exception {

        UUID source =
            insertOwnedStockLocation(
                "FEFO-S",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "FEFO-D",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "FEFO",
                true
            );

        UUID supplier =
            insertSupplier();

        insertBalance(
            item,
            source,
            "7.000",
            "0.000"
        );

        UUID early =
            insertLot(
                item,
                source,
                supplier,
                "LOT-EARLY",
                "2.000",
                "2.000",
                "2030-01-01T00:00:00Z",
                "1.20"
            );

        UUID later =
            insertLot(
                item,
                source,
                supplier,
                "LOT-LATER",
                "5.000",
                "5.000",
                "2031-01-01T00:00:00Z",
                "1.30"
            );

        UUID transferId =
            UUID.randomUUID();

        upsert(
            transferId,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "5.000"
            )
        );

        approve(transferId);

        dispatch(transferId)
            .andExpect(status().isOk());

        assertThat(
            lotRemaining(early)
        ).isEqualByComparingTo("0.000");

        assertThat(
            lotRemaining(later)
        ).isEqualByComparingTo("2.000");

        assertThat(
            movementLotDeltaSum(
                transferId,
                "TRANSFER_OUT"
            )
        ).isEqualByComparingTo("-5.000");

        receive(transferId)
            .andExpect(status().isOk());

        assertBalance(
            item,
            destination,
            "5.000",
            "0.000"
        );

        Long destinationLots =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_lots
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                Long.class,
                item,
                destination
            );

        assertThat(destinationLots)
            .isEqualTo(2L);

        BigDecimal destinationQuantity =
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    SUM(quantity_remaining),
                    0
                )
                FROM stock_lots
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                BigDecimal.class,
                item,
                destination
            );

        assertThat(destinationQuantity)
            .isEqualByComparingTo("5.000");

        Long preservedMetadata =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_lots
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                  AND supplier_id = ?
                  AND (
                        (
                            lot_number = 'LOT-EARLY'
                            AND unit_cost = 1.20
                            AND expires_at = TIMESTAMPTZ '2030-01-01 00:00:00+00'
                        )
                        OR
                        (
                            lot_number = 'LOT-LATER'
                            AND unit_cost = 1.30
                            AND expires_at = TIMESTAMPTZ '2031-01-01 00:00:00+00'
                        )
                      )
                """,
                Long.class,
                item,
                destination,
                supplier
            );

        assertThat(preservedMetadata)
            .isEqualTo(2L);

        assertThat(
            movementLotDeltaSum(
                transferId,
                "TRANSFER_IN"
            )
        ).isEqualByComparingTo("5.000");
    }

    @Test
    void expiryTrackedTransferRequiresCompleteLotCoverageAndRollsBack()
        throws Exception {

        UUID source =
            insertOwnedStockLocation(
                "LOT-COVER-S",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "LOT-COVER-D",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "LOT-COVER",
                true
            );

        insertBalance(
            item,
            source,
            "5.000",
            "0.000"
        );

        UUID lot =
            insertLot(
                item,
                source,
                null,
                "PARTIAL",
                "2.000",
                "2.000",
                "2030-01-01T00:00:00Z",
                null
            );

        UUID transferId =
            UUID.randomUUID();

        upsert(
            transferId,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "4.000"
            )
        );

        approve(transferId);

        dispatch(transferId)
            .andExpect(
                status().isConflict()
            );

        assertBalance(
            item,
            source,
            "5.000",
            "0.000"
        );

        assertThat(
            lotRemaining(lot)
        ).isEqualByComparingTo("2.000");

        assertThat(
            movementCount(
                transferId,
                "TRANSFER_OUT"
            )
        ).isZero();

        assertThat(
            movementLotDeltaSum(
                transferId,
                "TRANSFER_OUT"
            )
        ).isEqualByComparingTo("0.000");

        assertThat(
            transferStatus(
                transferId
            )
        ).isEqualTo("APPROVED");
    }

    @Test
    void nonExpiryTrackedItemCanTransferWithoutLots()
        throws Exception {

        UUID source =
            insertOwnedStockLocation(
                "NOLOT-S",
                true
            );

        UUID destination =
            insertOwnedStockLocation(
                "NOLOT-D",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "NOLOT",
                false
            );

        insertBalance(
            item,
            source,
            "5.000",
            "0.000"
        );

        UUID transferId =
            UUID.randomUUID();

        upsert(
            transferId,
            transferBody(
                source,
                destination,
                UUID.randomUUID(),
                item,
                "3.000"
            )
        );

        approve(transferId);
        dispatch(transferId);
        receive(transferId)
            .andExpect(status().isOk());

        assertBalance(
            item,
            source,
            "2.000",
            "0.000"
        );

        assertBalance(
            item,
            destination,
            "3.000",
            "0.000"
        );

        Long lots =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_lots
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                Long.class,
                item,
                destination
            );

        assertThat(lots)
            .isZero();
    }

    private ResultActions upsert(
        UUID transferId,
        String body
    ) throws Exception {

        return mockMvc.perform(
            put(
                "/api/v1/admin/inventory/transfers/{transferId}",
                transferId
            )
                .header(
                    "Authorization",
                    bearer("product.write")
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(body)
        );
    }

    private ResultActions approve(
        UUID transferId
    ) throws Exception {

        return transition(
            transferId,
            "approve"
        );
    }

    private ResultActions dispatch(
        UUID transferId
    ) throws Exception {

        return transition(
            transferId,
            "dispatch"
        );
    }

    private ResultActions receive(
        UUID transferId
    ) throws Exception {

        return transition(
            transferId,
            "receive"
        );
    }

    private ResultActions cancel(
        UUID transferId
    ) throws Exception {

        return transition(
            transferId,
            "cancel"
        );
    }

    private ResultActions transition(
        UUID transferId,
        String transition
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/inventory/transfers/{transferId}/{transition}",
                transferId,
                transition
            )
                .header(
                    "Authorization",
                    bearer("product.write")
                )
        );
    }

    private String transferBody(
        UUID source,
        UUID destination,
        UUID lineId,
        UUID stockItem,
        String quantity
    ) {

        return """
            {
              "sourceStockLocationId": "%s",
              "destinationStockLocationId": "%s",
              "reason": "Inventory transfer E2E",
              "lines": [
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": %s,
                  "unit": "PIECE"
                }
              ]
            }
            """.formatted(
                source,
                destination,
                lineId,
                stockItem,
                quantity
            );
    }

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
                    authSessionId
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

    private UUID insertOwnedStockItem(
        String prefix,
        boolean trackExpiry
    ) {

        return insertStockItemForOrganization(
            organizationId,
            prefix,
            trackExpiry
        );
    }

    private UUID insertStockItemForOrganization(
        UUID tenantId,
        String prefix,
        boolean trackExpiry
    ) {

        UUID categoryId =
            UUID.randomUUID();

        UUID productId =
            UUID.randomUUID();

        UUID stockItemId =
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
            categoryId,
            tenantId,
            prefix + " Category",
            "category-" + randomSuffix()
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
                ?, ?, ?, ?, ?,
                'PACKAGED',
                10.00,
                0.00,
                0,
                TRUE,
                FALSE,
                TRUE
            )
            """,
            productId,
            tenantId,
            categoryId,
            prefix + "-" + randomSuffix(),
            prefix + " Product"
        );

        jdbcTemplate.update(
            """
            INSERT INTO stock_items (
                id,
                organization_id,
                product_id,
                base_unit,
                track_expiry
            )
            VALUES (?, ?, ?, 'PIECE', ?)
            """,
            stockItemId,
            tenantId,
            productId,
            trackExpiry
        );

        return stockItemId;
    }

    private UUID insertOwnedStockLocation(
        String prefix,
        boolean active
    ) {

        return insertStockLocationForOrganization(
            organizationId,
            prefix,
            active
        );
    }

    private UUID insertStockLocationForOrganization(
        UUID tenantId,
        String prefix,
        boolean active
    ) {

        UUID campusId =
            UUID.randomUUID();

        UUID locationId =
            UUID.randomUUID();

        UUID stockLocationId =
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
            campusId,
            tenantId,
            prefix + " Campus",
            "C" + randomSuffix()
        );

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
            VALUES (?, ?, ?, ?, 'STORAGE', TRUE)
            """,
            locationId,
            campusId,
            prefix + " Location",
            "L" + randomSuffix()
        );

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
            stockLocationId,
            locationId,
            prefix + " Stock",
            active
        );

        return stockLocationId;
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
            prefix + " Org",
            "ORG" + randomSuffix()
        );

        return id;
    }

    private UUID insertUser(
        UUID tenantId
    ) {

        UUID id =
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
            VALUES (?, ?, ?, 'Foreign', 'Transfer', 'ACTIVE')
            """,
            id,
            tenantId,
            "foreign-transfer-"
                + randomSuffix()
                + "@sup2i.test"
        );

        return id;
    }

    private UUID insertSupplier() {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO suppliers (
                id,
                organization_id,
                name,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            id,
            organizationId,
            "Transfer Supplier "
                + randomSuffix()
        );

        return id;
    }

    private void insertBalance(
        UUID stockItem,
        UUID stockLocation,
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
            stockItem,
            stockLocation,
            new BigDecimal(physical),
            new BigDecimal(reserved)
        );
    }

    private UUID insertInventorySession(
        UUID stockLocation,
        String status
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO inventory_sessions (
                id,
                stock_location_id,
                status,
                started_by,
                started_at
            )
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            id,
            stockLocation,
            status,
            userId
        );

        return id;
    }

    private UUID insertLot(
        UUID stockItem,
        UUID stockLocation,
        UUID supplier,
        String lotNumber,
        String received,
        String remaining,
        String expiresAt,
        String unitCost
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_lots (
                id,
                stock_item_id,
                stock_location_id,
                supplier_id,
                lot_number,
                received_at,
                expires_at,
                quantity_received,
                quantity_remaining,
                unit_cost
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                CURRENT_TIMESTAMP,
                ?,
                ?,
                ?,
                ?
            )
            """,
            id,
            stockItem,
            stockLocation,
            supplier,
            lotNumber,
            OffsetDateTime.parse(expiresAt),
            new BigDecimal(received),
            new BigDecimal(remaining),
            unitCost == null
                ? null
                : new BigDecimal(unitCost)
        );

        return id;
    }

    private void assertBalance(
        UUID stockItem,
        UUID stockLocation,
        String physical,
        String reserved
    ) {

        BigDecimal actualPhysical =
            jdbcTemplate.queryForObject(
                """
                SELECT physical_quantity
                FROM stock_balances
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                BigDecimal.class,
                stockItem,
                stockLocation
            );

        BigDecimal actualReserved =
            jdbcTemplate.queryForObject(
                """
                SELECT reserved_quantity
                FROM stock_balances
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                BigDecimal.class,
                stockItem,
                stockLocation
            );

        assertThat(actualPhysical)
            .isEqualByComparingTo(physical);

        assertThat(actualReserved)
            .isEqualByComparingTo(reserved);
    }

    private String transferStatus(
        UUID transferId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM stock_transfers
            WHERE id = ?
            """,
            String.class,
            transferId
        );
    }

    private BigDecimal transferLineQuantity(
        UUID lineId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT quantity
            FROM stock_transfer_lines
            WHERE id = ?
            """,
            BigDecimal.class,
            lineId
        );
    }

    private Long movementCount(
        UUID transferId,
        String movementType
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_movements
            WHERE reference_type = 'STOCK_TRANSFER'
              AND reference_id = ?
              AND movement_type = ?
            """,
            Long.class,
            transferId,
            movementType
        );
    }

    private BigDecimal movementLotDeltaSum(
        UUID transferId,
        String movementType
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(
                SUM(ml.quantity_delta),
                0
            )
            FROM inventory_movement_lots ml
            JOIN inventory_movements m
              ON m.id = ml.inventory_movement_id
            WHERE m.reference_type = 'STOCK_TRANSFER'
              AND m.reference_id = ?
              AND m.movement_type = ?
            """,
            BigDecimal.class,
            transferId,
            movementType
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

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);
    }
}