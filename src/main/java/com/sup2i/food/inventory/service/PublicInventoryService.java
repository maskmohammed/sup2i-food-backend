package com.sup2i.food.inventory.service;

import com.sup2i.food.inventory.api.contract.InventoryAdjustmentRequest;
import com.sup2i.food.inventory.api.contract.InventoryItemResponse;
import com.sup2i.food.inventory.api.contract.InventoryMovementResponse;
import com.sup2i.food.inventory.api.dto.ApplyInventoryAdjustmentRequest;
import com.sup2i.food.inventory.api.dto.InventoryAdjustmentResponse;
import com.sup2i.food.inventory.api.dto.StockBalanceResponse;
import com.sup2i.food.inventory.api.dto.StockItemResponse;
import com.sup2i.food.inventory.domain.InventoryMovementType;
import com.sup2i.food.inventory.exception.InventoryConflictException;
import com.sup2i.food.inventory.exception.InventoryNotFoundException;
import com.sup2i.food.inventory.exception.InventoryPublicErrorCode;
import com.sup2i.food.inventory.exception.InventoryPublicException;
import com.sup2i.food.inventory.exception.InventoryValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PublicInventoryService {

    private static final String
        REFERENCE_TYPE =
            "MANUAL_ADJUSTMENT";

    private static final String
        IDEMPOTENCY_SCOPE =
            "PUBLIC_INVENTORY_ADJUSTMENT";

    private final InventoryService
        inventoryService;

    private final JdbcTemplate
        jdbcTemplate;

    public PublicInventoryService(
        InventoryService inventoryService,
        JdbcTemplate jdbcTemplate
    ) {

        this.inventoryService =
            inventoryService;

        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listItems(
        UUID actorId,
        UUID stockLocationId,
        boolean lowStockOnly
    ) {

        List<StockItemResponse> stockItems =
            inventoryService
                .findStockItems(
                    actorId
                );

        Map<UUID, StockItemResponse> itemsById =
            new HashMap<>();

        for (
            StockItemResponse stockItem
                : stockItems
        ) {

            itemsById.put(
                stockItem.id(),
                stockItem
            );
        }

        try {

            return inventoryService
                .findBalances(
                    actorId,
                    stockLocationId
                )
                .stream()
                .map(
                    balance ->
                        itemResponse(
                            balance,
                            itemsById.get(
                                balance.stockItemId()
                            )
                        )
                )
                .filter(
                    item ->
                        !lowStockOnly
                            || item.lowStock()
                )
                .toList();

        } catch (
            InventoryNotFoundException exception
        ) {

            throw new InventoryPublicException(
                InventoryPublicErrorCode
                    .RESOURCE_NOT_FOUND,
                exception.getMessage()
            );
        }
    }

    @Transactional
    public InventoryMovementResponse adjust(
        UUID actorId,
        String rawIdempotencyKey,
        InventoryAdjustmentRequest request
    ) {

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        String comment =
            normalizeNullableText(
                request.comment()
            );

        UUID internalKey =
            internalIdempotencyKey(
                actorId,
                idempotencyKey
            );

        lockIdempotency(
            actorId,
            idempotencyKey
        );

        List<StoredMovement> existing =
            findStoredMovement(
                internalKey
            );

        if (!existing.isEmpty()) {

            if (existing.size() != 1) {

                throw new InventoryPublicException(
                    InventoryPublicErrorCode
                        .CONCURRENT_MODIFICATION,
                    "Multiple inventory movements exist for one idempotency key."
                );
            }

            return replay(
                existing.get(0),
                actorId,
                request,
                comment
            );
        }

        StockItemResponse stockItem =
            inventoryService
                .findStockItems(
                    actorId
                )
                .stream()
                .filter(
                    item ->
                        item.id().equals(
                            request.stockItemId()
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                    new InventoryPublicException(
                        InventoryPublicErrorCode
                            .RESOURCE_NOT_FOUND,
                        "Stock item does not exist."
                    )
                );

        ApplyInventoryAdjustmentRequest internalRequest =
            new ApplyInventoryAdjustmentRequest(
                request.stockItemId(),
                request.stockLocationId(),
                request.quantityDelta(),
                stockItem.baseUnit(),
                null,
                internalKey,
                request.reason().name(),
                comment
            );

        try {

            InventoryAdjustmentResponse adjusted =
                inventoryService.adjust(
                    actorId,
                    internalRequest
                );

            return movementResponse(
                adjusted.movement()
            );

        } catch (
            InventoryNotFoundException exception
        ) {

            throw new InventoryPublicException(
                InventoryPublicErrorCode
                    .RESOURCE_NOT_FOUND,
                exception.getMessage()
            );

        } catch (
            InventoryValidationException exception
        ) {

            throw new InventoryPublicException(
                InventoryPublicErrorCode
                    .INVALID_STOCK_ADJUSTMENT,
                exception.getMessage()
            );

        } catch (
            InventoryConflictException exception
        ) {

            throw new InventoryPublicException(
                InventoryPublicErrorCode
                    .OUT_OF_STOCK,
                exception.getMessage()
            );
        }
    }

    private InventoryItemResponse itemResponse(
        StockBalanceResponse balance,
        StockItemResponse stockItem
    ) {

        if (stockItem == null) {

            throw new InventoryPublicException(
                InventoryPublicErrorCode
                    .CONCURRENT_MODIFICATION,
                "Stock balance references an unavailable stock item."
            );
        }

        String itemType =
            "INGREDIENT".equals(
                stockItem.subjectType()
            )
                ? "INGREDIENT"
                : "PRODUCT";

        BigDecimal threshold =
            stockItem.lowStockThreshold();

        boolean lowStock =
            balance
                .availableQuantity()
                .signum() <= 0
                || threshold != null
                && balance
                    .availableQuantity()
                    .compareTo(
                        threshold
                    ) <= 0;

        return new InventoryItemResponse(
            balance.stockItemId(),
            balance.stockLocationId(),
            itemType,
            stockItem.subjectName(),
            balance.unit(),
            balance.physicalQuantity(),
            balance.reservedQuantity(),
            balance.availableQuantity(),
            threshold,
            lowStock
        );
    }

    private InventoryMovementResponse movementResponse(
        com.sup2i.food.inventory.api.dto.InventoryMovementResponse
            movement
    ) {

        return new InventoryMovementResponse(
            movement.id(),
            movement.movementType(),
            movement.physicalDelta(),
            movement.reason(),
            movement.createdAt()
        );
    }

    private InventoryMovementResponse replay(
        StoredMovement existing,
        UUID actorId,
        InventoryAdjustmentRequest request,
        String comment
    ) {

        boolean same =
            Objects.equals(
                existing.performedBy(),
                actorId
            )
                && Objects.equals(
                    existing.stockItemId(),
                    request.stockItemId()
                )
                && Objects.equals(
                    existing.stockLocationId(),
                    request.stockLocationId()
                )
                && sameDecimal(
                    existing.quantity(),
                    request.quantityDelta()
                )
                && Objects.equals(
                    existing.reason(),
                    request.reason().name()
                )
                && Objects.equals(
                    existing.comment(),
                    comment
                )
                && existing.movementType()
                    == InventoryMovementType.ADJUSTMENT;

        if (!same) {

            throw new InventoryPublicException(
                InventoryPublicErrorCode
                    .IDEMPOTENCY_CONFLICT,
                "Idempotency key is already used with a different inventory request."
            );
        }

        return new InventoryMovementResponse(
            existing.id(),
            existing.movementType(),
            existing.quantity(),
            existing.reason(),
            existing.createdAt()
        );
    }

    private List<StoredMovement> findStoredMovement(
        UUID internalKey
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                id,
                stock_item_id,
                stock_location_id,
                movement_type,
                physical_delta,
                reason,
                comment,
                performed_by,
                created_at
            FROM inventory_movements
            WHERE reference_type = ?
              AND reference_id = ?
            ORDER BY created_at ASC, id ASC
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new StoredMovement(
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "stock_item_id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "stock_location_id",
                        UUID.class
                    ),
                    InventoryMovementType
                        .valueOf(
                            resultSet.getString(
                                "movement_type"
                            )
                        ),
                    resultSet.getBigDecimal(
                        "physical_delta"
                    ),
                    resultSet.getString(
                        "reason"
                    ),
                    resultSet.getString(
                        "comment"
                    ),
                    resultSet.getObject(
                        "performed_by",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "created_at",
                        OffsetDateTime.class
                    )
                ),
            REFERENCE_TYPE,
            internalKey
        );
    }

    private void lockIdempotency(
        UUID actorId,
        String idempotencyKey
    ) {

        byte[] digest =
            sha256Bytes(
                IDEMPOTENCY_SCOPE
                    + "\n"
                    + actorId
                    + "\n"
                    + idempotencyKey
            );

        long lockKey =
            ByteBuffer
                .wrap(
                    digest
                )
                .getLong();

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(?)",
            statement ->
                statement.setLong(
                    1,
                    lockKey
                ),
            (ResultSetExtractor<Void>)
                resultSet ->
                    null
        );
    }

    private UUID internalIdempotencyKey(
        UUID actorId,
        String idempotencyKey
    ) {

        byte[] digest =
            sha256Bytes(
                IDEMPOTENCY_SCOPE
                    + "\n"
                    + actorId
                    + "\n"
                    + idempotencyKey
            );

        ByteBuffer buffer =
            ByteBuffer.wrap(
                digest
            );

        return new UUID(
            buffer.getLong(),
            buffer.getLong()
        );
    }

    private String normalizeIdempotencyKey(
        String raw
    ) {

        String value =
            raw == null
                ? ""
                : raw.trim();

        if (
            value.length() < 8
                || value.length() > 160
        ) {

            throw new InventoryPublicException(
                InventoryPublicErrorCode
                    .INVALID_STOCK_ADJUSTMENT,
                "Idempotency-Key must contain between 8 and 160 characters."
            );
        }

        return value;
    }

    private String normalizeNullableText(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }

    private boolean sameDecimal(
        BigDecimal left,
        BigDecimal right
    ) {

        if (
            left == null
                || right == null
        ) {

            return left == null
                && right == null;
        }

        return left.compareTo(
            right
        ) == 0;
    }

    private byte[] sha256Bytes(
        String value
    ) {

        try {

            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            return digest.digest(
                value.getBytes(
                    StandardCharsets.UTF_8
                )
            );

        } catch (
            NoSuchAlgorithmException exception
        ) {

            throw new IllegalStateException(
                "SHA-256 is not available.",
                exception
            );
        }
    }

    private record StoredMovement(

        UUID id,

        UUID stockItemId,

        UUID stockLocationId,

        InventoryMovementType movementType,

        BigDecimal quantity,

        String reason,

        String comment,

        UUID performedBy,

        OffsetDateTime createdAt
    ) {
    }
}