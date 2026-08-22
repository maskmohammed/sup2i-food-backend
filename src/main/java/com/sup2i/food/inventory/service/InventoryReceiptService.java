package com.sup2i.food.inventory.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.inventory.api.dto.ReceiveStockLineRequest;
import com.sup2i.food.inventory.api.dto.ReceiveStockRequest;
import com.sup2i.food.inventory.api.dto.StockLotResponse;
import com.sup2i.food.inventory.api.dto.StockReceiptLineResponse;
import com.sup2i.food.inventory.api.dto.StockReceiptResponse;
import com.sup2i.food.inventory.api.dto.UpsertStockReceiptResponse;
import com.sup2i.food.inventory.domain.InventoryMovement;
import com.sup2i.food.inventory.domain.InventoryMovementLot;
import com.sup2i.food.inventory.domain.InventoryMovementType;
import com.sup2i.food.inventory.domain.StockBalance;
import com.sup2i.food.inventory.domain.StockBalanceId;
import com.sup2i.food.inventory.domain.StockItem;
import com.sup2i.food.inventory.domain.StockLocation;
import com.sup2i.food.inventory.domain.StockLot;
import com.sup2i.food.inventory.domain.StockReceipt;
import com.sup2i.food.inventory.domain.StockReceiptLine;
import com.sup2i.food.inventory.domain.StockReceiptStatus;
import com.sup2i.food.inventory.exception.InventoryConflictException;
import com.sup2i.food.inventory.exception.InventoryNotFoundException;
import com.sup2i.food.inventory.exception.InventoryValidationException;
import com.sup2i.food.inventory.repository.InventoryMovementLotRepository;
import com.sup2i.food.inventory.repository.InventoryMovementRepository;
import com.sup2i.food.inventory.repository.StockBalanceRepository;
import com.sup2i.food.inventory.repository.StockItemRepository;
import com.sup2i.food.inventory.repository.StockLocationRepository;
import com.sup2i.food.inventory.repository.StockLotRepository;
import com.sup2i.food.inventory.repository.StockReceiptLineRepository;
import com.sup2i.food.inventory.repository.StockReceiptRepository;
import com.sup2i.food.procurement.domain.Supplier;
import com.sup2i.food.procurement.repository.SupplierRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryReceiptService {

    private static final String
        RECEIPT_REFERENCE_TYPE =
            "STOCK_RECEIPT";

    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;
    private final StockLocationRepository stockLocationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockLotRepository stockLotRepository;
    private final InventoryMovementLotRepository movementLotRepository;
    private final StockReceiptRepository receiptRepository;
    private final StockReceiptLineRepository receiptLineRepository;
    private final JdbcTemplate jdbcTemplate;

    public InventoryReceiptService(
        UserRepository userRepository,
        SupplierRepository supplierRepository,
        StockLocationRepository stockLocationRepository,
        StockItemRepository stockItemRepository,
        StockBalanceRepository stockBalanceRepository,
        InventoryMovementRepository movementRepository,
        StockLotRepository stockLotRepository,
        InventoryMovementLotRepository movementLotRepository,
        StockReceiptRepository receiptRepository,
        StockReceiptLineRepository receiptLineRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.userRepository =
            userRepository;

        this.supplierRepository =
            supplierRepository;

        this.stockLocationRepository =
            stockLocationRepository;

        this.stockItemRepository =
            stockItemRepository;

        this.stockBalanceRepository =
            stockBalanceRepository;

        this.movementRepository =
            movementRepository;

        this.stockLotRepository =
            stockLotRepository;

        this.movementLotRepository =
            movementLotRepository;

        this.receiptRepository =
            receiptRepository;

        this.receiptLineRepository =
            receiptLineRepository;

        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public UpsertStockReceiptResponse receive(
        UUID actorId,
        UUID receiptId,
        ReceiveStockRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        validateUniqueLineIds(
            request.lines()
        );

        lockReceiptId(receiptId);

        StockReceipt existing =
            receiptRepository
                .findOwnedById(
                    receiptId,
                    organizationId
                )
                .orElse(null);

        if (existing != null) {

            return replay(
                existing,
                request
            );
        }

        if (
            receiptRepository
                .existsById(receiptId)
        ) {
            throw new InventoryNotFoundException(
                "Stock receipt does not exist."
            );
        }

        StockLocation stockLocation =
            stockLocationRepository
                .findByIdAndLocation_Campus_Organization_Id(
                    request.stockLocationId(),
                    organizationId
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Stock location does not exist."
                    )
                );

        if (!stockLocation.isActive()) {
            throw new InventoryValidationException(
                "Stock location is not active."
            );
        }

        Supplier supplier =
            resolveSupplier(
                request.supplierId(),
                organizationId
            );

        List<UUID> uniqueItemIds =
            request.lines()
                .stream()
                .map(
                    ReceiveStockLineRequest::stockItemId
                )
                .distinct()
                .sorted()
                .toList();

        List<StockItem> stockItems =
            stockItemRepository
                .findAllByIdInAndOrganization_Id(
                    uniqueItemIds,
                    organizationId
                );

        if (
            stockItems.size()
            != uniqueItemIds.size()
        ) {
            throw new InventoryNotFoundException(
                "One or more stock items do not exist."
            );
        }

        Map<UUID, StockItem> stockItemsById =
            stockItems
                .stream()
                .collect(
                    Collectors.toMap(
                        StockItem::getId,
                        Function.identity()
                    )
                );

        OffsetDateTime receivedAt =
            OffsetDateTime.now();

        for (
            ReceiveStockLineRequest line
            : request.lines()
        ) {
            validateLine(
                line,
                stockItemsById.get(
                    line.stockItemId()
                ),
                receivedAt
            );
        }

        List<UUID> lineIds =
            request.lines()
                .stream()
                .map(
                    ReceiveStockLineRequest::lineId
                )
                .toList();

        if (
            !receiptLineRepository
                .findAllByIdIn(lineIds)
                .isEmpty()
        ) {
            throw new InventoryConflictException(
                "One or more receipt line identifiers are already in use."
            );
        }

        Map<UUID, StockBalance> balances =
            lockBalances(
                uniqueItemIds,
                stockItemsById,
                stockLocation
            );

        StockReceipt receipt =
            new StockReceipt(
                receiptId,
                stockLocation,
                supplier,
                normalizeNullableText(
                    request.receiptReference()
                ),
                receivedAt,
                actor,
                normalizeNullableText(
                    request.notes()
                )
            );

        List<StockReceiptLine>
            createdLines =
                new ArrayList<>();

        try {

            receiptRepository.save(receipt);

            for (
                ReceiveStockLineRequest line
                : request.lines()
            ) {

                StockItem stockItem =
                    stockItemsById.get(
                        line.stockItemId()
                    );

                StockBalance balance =
                    balances.get(
                        stockItem.getId()
                    );

                balance.applyPhysicalDelta(
                    line.quantity()
                );

                stockBalanceRepository.save(
                    balance
                );

                InventoryMovement movement =
                    new InventoryMovement(
                        stockItem,
                        stockLocation,
                        InventoryMovementType.PURCHASE_IN,
                        line.quantity(),
                        BigDecimal.ZERO,
                        line.unit(),
                        line.unitCost(),
                        RECEIPT_REFERENCE_TYPE,
                        receiptId,
                        "Stock receipt",
                        normalizeNullableText(
                            request.receiptReference()
                        ),
                        actor
                    );

                movement =
                    movementRepository.save(
                        movement
                    );

                StockLot lot =
                    new StockLot(
                        stockItem,
                        stockLocation,
                        normalizeNullableText(
                            line.lotNumber()
                        ),
                        supplier,
                        receivedAt,
                        line.expiresAt(),
                        line.quantity(),
                        line.unitCost()
                    );

                lot =
                    stockLotRepository.save(
                        lot
                    );

                movementLotRepository.save(
                    new InventoryMovementLot(
                        movement,
                        lot,
                        line.quantity()
                    )
                );

                StockReceiptLine receiptLine =
                    new StockReceiptLine(
                        line.lineId(),
                        receipt,
                        stockItem,
                        line.quantity(),
                        line.unit(),
                        line.unitCost(),
                        normalizeNullableText(
                            line.lotNumber()
                        ),
                        line.expiresAt(),
                        lot,
                        movement
                    );

                receiptLineRepository.save(
                    receiptLine
                );

                createdLines.add(
                    receiptLine
                );
            }

            /*
             * Flush through a Spring Data repository so database
             * constraint failures are translated consistently.
             */
            receiptRepository.flush();

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new InventoryConflictException(
                "Stock receipt violates an inventory invariant."
            );
        }

        createdLines.sort(
            Comparator.comparing(
                StockReceiptLine::getId
            )
        );

        return new UpsertStockReceiptResponse(
            response(
                receipt,
                createdLines
            ),
            false
        );
    }

    @Transactional(readOnly = true)
    public StockReceiptResponse findReceipt(
        UUID actorId,
        UUID receiptId
    ) {

        UUID organizationId =
            authenticatedUser(actorId)
                .getOrganization()
                .getId();

        StockReceipt receipt =
            receiptRepository
                .findOwnedById(
                    receiptId,
                    organizationId
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Stock receipt does not exist."
                    )
                );

        return response(
            receipt,
            receiptLineRepository
                .findAllForReceipt(
                    receiptId
                )
        );
    }

    @Transactional(readOnly = true)
    public List<StockLotResponse> findLots(
        UUID actorId,
        UUID stockLocationId,
        UUID stockItemId,
        boolean remainingOnly
    ) {

        UUID organizationId =
            authenticatedUser(actorId)
                .getOrganization()
                .getId();

        if (stockLocationId != null) {

            stockLocationRepository
                .findByIdAndLocation_Campus_Organization_Id(
                    stockLocationId,
                    organizationId
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Stock location does not exist."
                    )
                );
        }

        if (stockItemId != null) {

            stockItemRepository
                .findByIdAndOrganization_Id(
                    stockItemId,
                    organizationId
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Stock item does not exist."
                    )
                );
        }

        return stockLotRepository
            .search(
                organizationId,
                stockLocationId,
                stockItemId,
                remainingOnly
            )
            .stream()
            .map(this::lotResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public StockLotResponse findLot(
        UUID actorId,
        UUID lotId
    ) {

        UUID organizationId =
            authenticatedUser(actorId)
                .getOrganization()
                .getId();

        StockLot lot =
            stockLotRepository
                .findOwnedById(
                    lotId,
                    organizationId
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Stock lot does not exist."
                    )
                );

        return lotResponse(lot);
    }

    private UpsertStockReceiptResponse replay(
        StockReceipt receipt,
        ReceiveStockRequest request
    ) {

        List<StockReceiptLine> lines =
            receiptLineRepository
                .findAllForReceipt(
                    receipt.getId()
                );

        if (
            receipt.getStatus()
                != StockReceiptStatus.RECEIVED
            || !sameUuid(
                request.stockLocationId(),
                receipt.getStockLocation()
                    .getId()
            )
            || !sameUuid(
                request.supplierId(),
                receipt.getSupplier() == null
                    ? null
                    : receipt.getSupplier()
                        .getId()
            )
            || !Objects.equals(
                normalizeNullableText(
                    request.receiptReference()
                ),
                receipt.getReceiptReference()
            )
            || !Objects.equals(
                normalizeNullableText(
                    request.notes()
                ),
                receipt.getNotes()
            )
            || !sameLines(
                lines,
                request.lines()
            )
        ) {
            throw new InventoryConflictException(
                "Receipt identifier is already used by another payload."
            );
        }

        return new UpsertStockReceiptResponse(
            response(
                receipt,
                lines
            ),
            true
        );
    }

    private boolean sameLines(
        List<StockReceiptLine> existing,
        List<ReceiveStockLineRequest> requested
    ) {

        if (
            existing.size()
            != requested.size()
        ) {
            return false;
        }

        Map<UUID, StockReceiptLine> byId =
            existing
                .stream()
                .collect(
                    Collectors.toMap(
                        StockReceiptLine::getId,
                        Function.identity()
                    )
                );

        for (
            ReceiveStockLineRequest line
            : requested
        ) {

            StockReceiptLine stored =
                byId.get(
                    line.lineId()
                );

            if (stored == null) {
                return false;
            }

            if (
                !sameUuid(
                    line.stockItemId(),
                    stored.getStockItem()
                        .getId()
                )
                || !sameDecimal(
                    line.quantity(),
                    stored.getQuantity()
                )
                || line.unit()
                    != stored.getUnit()
                || !sameDecimal(
                    line.unitCost(),
                    stored.getUnitCost()
                )
                || !Objects.equals(
                    normalizeNullableText(
                        line.lotNumber()
                    ),
                    stored.getLotNumber()
                )
                || !sameInstant(
                    line.expiresAt(),
                    stored.getExpiresAt()
                )
            ) {
                return false;
            }
        }

        return true;
    }

    private Map<UUID, StockBalance>
        lockBalances(
            List<UUID> sortedStockItemIds,
            Map<UUID, StockItem> stockItems,
            StockLocation stockLocation
        ) {

        Map<UUID, StockBalance> balances =
            new LinkedHashMap<>();

        for (
            UUID stockItemId
            : sortedStockItemIds
        ) {

            StockItem stockItem =
                stockItems.get(
                    stockItemId
                );

            stockBalanceRepository
                .ensureExists(
                    stockItemId,
                    stockLocation.getId()
                );

            StockBalanceId balanceId =
                new StockBalanceId(
                    stockItemId,
                    stockLocation.getId()
                );

            StockBalance balance =
                stockBalanceRepository
                    .findLockedById(
                        balanceId
                    )
                    .orElseThrow(() ->
                        new InventoryConflictException(
                            "Stock balance could not be locked."
                        )
                    );

            balances.put(
                stockItemId,
                balance
            );
        }

        return balances;
    }

    private void validateUniqueLineIds(
        List<ReceiveStockLineRequest> lines
    ) {

        if (lines == null) {
            return;
        }

        HashSet<UUID> identifiers =
            new HashSet<>();

        for (
            ReceiveStockLineRequest line
            : lines
        ) {

            if (
                line == null
                || line.lineId() == null
            ) {
                continue;
            }

            if (
                !identifiers.add(
                    line.lineId()
                )
            ) {
                throw new InventoryValidationException(
                    "Receipt line identifiers must be unique."
                );
            }
        }
    }

    private void validateLine(
        ReceiveStockLineRequest line,
        StockItem stockItem,
        OffsetDateTime receivedAt
    ) {

        if (stockItem == null) {
            throw new InventoryNotFoundException(
                "Stock item does not exist."
            );
        }

        if (
            line.unit()
            != stockItem.getBaseUnit()
        ) {
            throw new InventoryValidationException(
                "Receipt line unit must match the stock item base unit."
            );
        }

        if (!isTrackingEnabled(stockItem)) {
            throw new InventoryValidationException(
                "Stock item subject is not configured to track stock."
            );
        }

        if (
            stockItem.isTrackExpiry()
            && line.expiresAt() == null
        ) {
            throw new InventoryValidationException(
                "Expiry date is required for this stock item."
            );
        }

        if (
            line.expiresAt() != null
            && !line.expiresAt()
                .isAfter(receivedAt)
        ) {
            throw new InventoryValidationException(
                "Expiry date must be after receipt time."
            );
        }
    }

    private boolean isTrackingEnabled(
        StockItem stockItem
    ) {

        if (
            stockItem.getProduct() != null
        ) {
            return stockItem
                .getProduct()
                .isTrackStock();
        }

        if (
            stockItem.getVariant() != null
        ) {
            return stockItem
                .getVariant()
                .getProduct()
                .isTrackStock();
        }

        if (
            stockItem.getIngredient() != null
        ) {
            return stockItem
                .getIngredient()
                .isTrackStock();
        }

        return false;
    }

    private Supplier resolveSupplier(
        UUID supplierId,
        UUID organizationId
    ) {

        if (supplierId == null) {
            return null;
        }

        return supplierRepository
            .findByIdAndOrganization_IdAndActiveTrue(
                supplierId,
                organizationId
            )
            .orElseThrow(() ->
                new InventoryNotFoundException(
                    "Supplier does not exist."
                )
            );
    }

    private User authenticatedUser(
        UUID userId
    ) {

        return userRepository
            .findById(userId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private void lockReceiptId(
        UUID receiptId
    ) {

        long lockKey =
            receiptId
                .getMostSignificantBits()
            ^ receiptId
                .getLeastSignificantBits();

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(?)",
            statement ->
                statement.setLong(
                    1,
                    lockKey
                ),
            (ResultSetExtractor<Void>)
                resultSet -> null
        );
    }

    private StockReceiptResponse response(
        StockReceipt receipt,
        List<StockReceiptLine> lines
    ) {

        List<StockReceiptLineResponse>
            lineResponses =
                lines
                    .stream()
                    .sorted(
                        Comparator.comparing(
                            StockReceiptLine::getId
                        )
                    )
                    .map(
                        this::lineResponse
                    )
                    .toList();

        Supplier supplier =
            receipt.getSupplier();

        return new StockReceiptResponse(
            receipt.getId(),
            receipt.getStockLocation()
                .getId(),
            supplier == null
                ? null
                : supplier.getId(),
            supplier == null
                ? null
                : supplier.getName(),
            receipt.getReceiptReference(),
            receipt.getReceivedAt(),
            receipt.getReceivedBy()
                .getId(),
            receipt.getStatus(),
            receipt.getNotes(),
            lineResponses
        );
    }

    private StockReceiptLineResponse
        lineResponse(
            StockReceiptLine line
        ) {

        return new StockReceiptLineResponse(
            line.getId(),
            line.getStockItem()
                .getId(),
            line.getQuantity(),
            line.getUnit(),
            line.getUnitCost(),
            line.getLotNumber(),
            line.getExpiresAt(),
            line.getGeneratedLot() == null
                ? null
                : line.getGeneratedLot()
                    .getId(),
            line.getInventoryMovement() == null
                ? null
                : line.getInventoryMovement()
                    .getId()
        );
    }

    private StockLotResponse lotResponse(
        StockLot lot
    ) {

        Supplier supplier =
            lot.getSupplier();

        return new StockLotResponse(
            lot.getId(),
            lot.getStockItem()
                .getId(),
            lot.getStockLocation()
                .getId(),
            lot.getLotNumber(),
            supplier == null
                ? null
                : supplier.getId(),
            supplier == null
                ? null
                : supplier.getName(),
            lot.getReceivedAt(),
            lot.getExpiresAt(),
            lot.getQuantityReceived(),
            lot.getQuantityRemaining(),
            lot.getUnitCost()
        );
    }

    private boolean sameUuid(
        UUID left,
        UUID right
    ) {
        return Objects.equals(
            left,
            right
        );
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

        return left.compareTo(right) == 0;
    }

    private boolean sameInstant(
        OffsetDateTime left,
        OffsetDateTime right
    ) {

        if (
            left == null
            || right == null
        ) {
            return left == null
                && right == null;
        }

        return left.toInstant()
            .equals(
                right.toInstant()
            );
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
}