package com.sup2i.food.inventory.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.inventory.api.dto.CreateStockTransferLineRequest;
import com.sup2i.food.inventory.api.dto.CreateStockTransferRequest;
import com.sup2i.food.inventory.api.dto.StockTransferLineResponse;
import com.sup2i.food.inventory.api.dto.StockTransferMutationResponse;
import com.sup2i.food.inventory.api.dto.StockTransferResponse;
import com.sup2i.food.inventory.domain.InventoryMovement;
import com.sup2i.food.inventory.domain.InventoryMovementLot;
import com.sup2i.food.inventory.domain.InventoryMovementType;
import com.sup2i.food.inventory.domain.InventorySessionStatus;
import com.sup2i.food.inventory.domain.StockBalance;
import com.sup2i.food.inventory.domain.StockBalanceId;
import com.sup2i.food.inventory.domain.StockItem;
import com.sup2i.food.inventory.domain.StockLocation;
import com.sup2i.food.inventory.domain.StockLot;
import com.sup2i.food.inventory.domain.StockTransfer;
import com.sup2i.food.inventory.domain.StockTransferLine;
import com.sup2i.food.inventory.domain.StockTransferStatus;
import com.sup2i.food.inventory.exception.InventoryConflictException;
import com.sup2i.food.inventory.exception.InventoryNotFoundException;
import com.sup2i.food.inventory.exception.InventoryValidationException;
import com.sup2i.food.inventory.repository.InventoryMovementLotRepository;
import com.sup2i.food.inventory.repository.InventoryMovementRepository;
import com.sup2i.food.inventory.repository.InventorySessionRepository;
import com.sup2i.food.inventory.repository.StockBalanceRepository;
import com.sup2i.food.inventory.repository.StockItemRepository;
import com.sup2i.food.inventory.repository.StockLocationRepository;
import com.sup2i.food.inventory.repository.StockLotRepository;
import com.sup2i.food.inventory.repository.StockTransferLineRepository;
import com.sup2i.food.inventory.repository.StockTransferRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryTransferService {

    private static final String REFERENCE_TYPE =
        "STOCK_TRANSFER";

    private static final Set<InventorySessionStatus>
        ACTIVE_INVENTORY_STATUSES =
            Set.of(
                InventorySessionStatus.OPEN,
                InventorySessionStatus.COUNTING,
                InventorySessionStatus.COMPLETED
            );

    private final UserRepository userRepository;
    private final StockLocationRepository stockLocationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockLotRepository stockLotRepository;
    private final InventoryMovementLotRepository movementLotRepository;
    private final InventorySessionRepository inventorySessionRepository;
    private final StockTransferRepository transferRepository;
    private final StockTransferLineRepository lineRepository;
    private final JdbcTemplate jdbcTemplate;

    public InventoryTransferService(
        UserRepository userRepository,
        StockLocationRepository stockLocationRepository,
        StockItemRepository stockItemRepository,
        StockBalanceRepository stockBalanceRepository,
        InventoryMovementRepository movementRepository,
        StockLotRepository stockLotRepository,
        InventoryMovementLotRepository movementLotRepository,
        InventorySessionRepository inventorySessionRepository,
        StockTransferRepository transferRepository,
        StockTransferLineRepository lineRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.userRepository =
            userRepository;

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

        this.inventorySessionRepository =
            inventorySessionRepository;

        this.transferRepository =
            transferRepository;

        this.lineRepository =
            lineRepository;

        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public StockTransferMutationResponse upsertDraft(
        UUID actorId,
        UUID transferId,
        CreateStockTransferRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        validateUniqueLines(
            request.lines()
        );

        lockTransferId(
            transferId
        );

        StockTransfer existing =
            transferRepository
                .findOwnedByIdForUpdate(
                    transferId,
                    organizationId
                )
                .orElse(null);

        if (existing != null) {

            if (
                samePayload(
                    existing,
                    request
                )
            ) {
                return new StockTransferMutationResponse(
                    response(existing),
                    true
                );
            }

            if (
                existing.getStatus()
                    != StockTransferStatus.DRAFT
            ) {
                throw new InventoryConflictException(
                    "Approved or processed stock transfers are immutable."
                );
            }

            return reviseDraft(
                existing,
                organizationId,
                request
            );
        }

        if (
            transferRepository
                .existsById(transferId)
        ) {
            throw new InventoryNotFoundException(
                "Stock transfer does not exist."
            );
        }

        StockLocation source =
            ownedActiveLocation(
                request.sourceStockLocationId(),
                organizationId
            );

        StockLocation destination =
            ownedActiveLocation(
                request.destinationStockLocationId(),
                organizationId
            );

        Map<UUID, StockItem> stockItems =
            resolveStockItems(
                request.lines(),
                organizationId
            );

        validateLinesAgainstItems(
            request.lines(),
            stockItems
        );

        validateLineIdentifiersAvailable(
            transferId,
            request.lines()
        );

        StockTransfer transfer =
            new StockTransfer(
                transferId,
                source,
                destination,
                actor,
                normalizeNullableText(
                    request.reason()
                )
            );

        try {

            transferRepository.save(
                transfer
            );

            saveReplacementLines(
                transfer,
                request.lines(),
                stockItems
            );

            transferRepository.flush();

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new InventoryConflictException(
                "Stock transfer conflicts with an existing inventory resource."
            );
        }

        return new StockTransferMutationResponse(
            response(transfer),
            false
        );
    }

    @Transactional
    public StockTransferMutationResponse approve(
        UUID actorId,
        UUID transferId
    ) {

        User actor =
            authenticatedUser(actorId);

        StockTransfer transfer =
            transferForUpdate(
                transferId,
                actor.getOrganization()
                    .getId()
            );

        if (
            transfer.getStatus()
                == StockTransferStatus.APPROVED
        ) {
            return mutationReplay(
                transfer
            );
        }

        if (
            transfer.getStatus()
                != StockTransferStatus.DRAFT
        ) {
            throw new InventoryConflictException(
                "Only a draft stock transfer can be approved."
            );
        }

        ensureLocationActive(
            transfer.getSourceStockLocation(),
            "Source"
        );

        ensureLocationActive(
            transfer.getDestinationStockLocation(),
            "Destination"
        );

        List<StockTransferLine> lines =
            orderedLines(
                transferId
            );

        if (lines.isEmpty()) {
            throw new InventoryConflictException(
                "Stock transfer contains no lines."
            );
        }

        for (
            StockTransferLine line
            : lines
        ) {

            StockItem item =
                line.getStockItem();

            if (!isTrackingEnabled(item)) {
                throw new InventoryValidationException(
                    "Stock item subject is not configured to track stock."
                );
            }

            BigDecimal available =
                currentAvailable(
                    item.getId(),
                    transfer
                        .getSourceStockLocation()
                        .getId()
                );

            if (
                line.getQuantity()
                    .compareTo(available)
                > 0
            ) {
                throw new InventoryConflictException(
                    "Insufficient available stock to approve transfer."
                );
            }
        }

        transfer.approve(actor);

        transferRepository
            .saveAndFlush(transfer);

        return mutationChanged(
            transfer
        );
    }

    @Transactional
    public StockTransferMutationResponse dispatch(
        UUID actorId,
        UUID transferId
    ) {

        User actor =
            authenticatedUser(actorId);

        StockTransfer transfer =
            transferForUpdate(
                transferId,
                actor.getOrganization()
                    .getId()
            );

        if (
            transfer.getStatus()
                == StockTransferStatus.IN_TRANSIT
        ) {
            return mutationReplay(
                transfer
            );
        }

        if (
            transfer.getStatus()
                != StockTransferStatus.APPROVED
        ) {
            throw new InventoryConflictException(
                "Only an approved stock transfer can be dispatched."
            );
        }

        StockLocation source =
            transfer.getSourceStockLocation();

        ensureLocationActive(
            source,
            "Source"
        );

        lockStockLocation(
            source.getId()
        );

        ensureNoActiveInventorySession(
            source.getId(),
            "source"
        );

        List<StockTransferLine> lines =
            orderedLines(
                transferId
            );

        try {

            for (
                StockTransferLine line
                : lines
            ) {

                StockItem item =
                    line.getStockItem();

                if (!isTrackingEnabled(item)) {
                    throw new InventoryValidationException(
                        "Stock item subject is not configured to track stock."
                    );
                }

                StockBalance balance =
                    lockedBalance(
                        item.getId(),
                        source.getId()
                    );

                BigDecimal available =
                    balance
                        .getPhysicalQuantity()
                        .subtract(
                            balance
                                .getReservedQuantity()
                        );

                if (
                    line.getQuantity()
                        .compareTo(available)
                    > 0
                ) {
                    throw new InventoryConflictException(
                        "Insufficient available stock for transfer."
                    );
                }

                InventoryMovement movement =
                    new InventoryMovement(
                        item,
                        source,
                        InventoryMovementType.TRANSFER_OUT,
                        line.getQuantity()
                            .negate(),
                        BigDecimal.ZERO,
                        item.getBaseUnit(),
                        null,
                        REFERENCE_TYPE,
                        transferId,
                        "Stock transfer dispatch",
                        transfer.getReason(),
                        actor
                    );

                movement =
                    movementRepository.save(
                        movement
                    );

                allocateSourceLots(
                    item,
                    source,
                    movement,
                    line.getQuantity()
                );

                balance.applyPhysicalDelta(
                    line.getQuantity()
                        .negate()
                );

                stockBalanceRepository.save(
                    balance
                );

                line.attachTransferOutMovement(
                    movement
                );

                lineRepository.save(
                    line
                );
            }

            transfer.dispatch(actor);

            transferRepository
                .saveAndFlush(transfer);

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new InventoryConflictException(
                "Stock transfer dispatch violates an inventory invariant."
            );
        }

        return mutationChanged(
            transfer
        );
    }

    @Transactional
    public StockTransferMutationResponse receive(
        UUID actorId,
        UUID transferId
    ) {

        User actor =
            authenticatedUser(actorId);

        StockTransfer transfer =
            transferForUpdate(
                transferId,
                actor.getOrganization()
                    .getId()
            );

        if (
            transfer.getStatus()
                == StockTransferStatus.RECEIVED
        ) {
            return mutationReplay(
                transfer
            );
        }

        if (
            transfer.getStatus()
                != StockTransferStatus.IN_TRANSIT
        ) {
            throw new InventoryConflictException(
                "Only an in-transit stock transfer can be received."
            );
        }

        StockLocation destination =
            transfer
                .getDestinationStockLocation();

        ensureLocationActive(
            destination,
            "Destination"
        );

        lockStockLocation(
            destination.getId()
        );

        ensureNoActiveInventorySession(
            destination.getId(),
            "destination"
        );

        List<StockTransferLine> lines =
            orderedLines(
                transferId
            );

        OffsetDateTime receivedAt =
            OffsetDateTime.now();

        try {

            for (
                StockTransferLine line
                : lines
            ) {

                if (
                    line.getTransferOutMovement()
                    == null
                ) {
                    throw new InventoryConflictException(
                        "Transfer line has no dispatch movement."
                    );
                }

                if (
                    line.getTransferInMovement()
                    != null
                ) {
                    throw new InventoryConflictException(
                        "Transfer line is already received."
                    );
                }

                StockItem item =
                    line.getStockItem();

                StockBalance balance =
                    lockedBalance(
                        item.getId(),
                        destination.getId()
                    );

                InventoryMovement movement =
                    new InventoryMovement(
                        item,
                        destination,
                        InventoryMovementType.TRANSFER_IN,
                        line.getQuantity(),
                        BigDecimal.ZERO,
                        item.getBaseUnit(),
                        null,
                        REFERENCE_TYPE,
                        transferId,
                        "Stock transfer receipt",
                        transfer.getReason(),
                        actor
                    );

                movement =
                    movementRepository.save(
                        movement
                    );

                recreateDestinationLots(
                    line,
                    destination,
                    movement,
                    receivedAt
                );

                balance.applyPhysicalDelta(
                    line.getQuantity()
                );

                stockBalanceRepository.save(
                    balance
                );

                line.attachTransferInMovement(
                    movement
                );

                lineRepository.save(
                    line
                );
            }

            transfer.receive(actor);

            transferRepository
                .saveAndFlush(transfer);

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new InventoryConflictException(
                "Stock transfer receipt violates an inventory invariant."
            );
        }

        return mutationChanged(
            transfer
        );
    }

    @Transactional
    public StockTransferMutationResponse cancel(
        UUID actorId,
        UUID transferId
    ) {

        User actor =
            authenticatedUser(actorId);

        StockTransfer transfer =
            transferForUpdate(
                transferId,
                actor.getOrganization()
                    .getId()
            );

        if (
            transfer.getStatus()
                == StockTransferStatus.CANCELLED
        ) {
            return mutationReplay(
                transfer
            );
        }

        boolean cancellable =
            transfer.getStatus()
                == StockTransferStatus.DRAFT
            || transfer.getStatus()
                == StockTransferStatus.APPROVED;

        if (!cancellable) {
            throw new InventoryConflictException(
                "Dispatched or received stock transfer cannot be cancelled."
            );
        }

        transfer.cancel();

        transferRepository
            .saveAndFlush(transfer);

        return mutationChanged(
            transfer
        );
    }

    @Transactional(readOnly = true)
    public StockTransferResponse find(
        UUID actorId,
        UUID transferId
    ) {

        User actor =
            authenticatedUser(actorId);

        StockTransfer transfer =
            transferRepository
                .findOwnedById(
                    transferId,
                    actor.getOrganization()
                        .getId()
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Stock transfer does not exist."
                    )
                );

        return response(
            transfer
        );
    }

    private StockTransferMutationResponse
        reviseDraft(
            StockTransfer transfer,
            UUID organizationId,
            CreateStockTransferRequest request
        ) {

        StockLocation source =
            ownedActiveLocation(
                request.sourceStockLocationId(),
                organizationId
            );

        StockLocation destination =
            ownedActiveLocation(
                request.destinationStockLocationId(),
                organizationId
            );

        Map<UUID, StockItem> stockItems =
            resolveStockItems(
                request.lines(),
                organizationId
            );

        validateLinesAgainstItems(
            request.lines(),
            stockItems
        );

        validateLineIdentifiersAvailable(
            transfer.getId(),
            request.lines()
        );

        List<StockTransferLine> oldLines =
            lineRepository
                .findAllForTransfer(
                    transfer.getId()
                );

        try {

            lineRepository.deleteAll(
                oldLines
            );

            lineRepository.flush();

            transfer.revise(
                source,
                destination,
                normalizeNullableText(
                    request.reason()
                )
            );

            transferRepository.save(
                transfer
            );

            saveReplacementLines(
                transfer,
                request.lines(),
                stockItems
            );

            transferRepository.flush();

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new InventoryConflictException(
                "Stock transfer revision conflicts with an existing inventory resource."
            );
        }

        return mutationChanged(
            transfer
        );
    }

    private boolean samePayload(
        StockTransfer transfer,
        CreateStockTransferRequest request
    ) {

        if (
            !Objects.equals(
                transfer
                    .getSourceStockLocation()
                    .getId(),
                request.sourceStockLocationId()
            )
            || !Objects.equals(
                transfer
                    .getDestinationStockLocation()
                    .getId(),
                request.destinationStockLocationId()
            )
            || !Objects.equals(
                transfer.getReason(),
                normalizeNullableText(
                    request.reason()
                )
            )
        ) {
            return false;
        }

        List<StockTransferLine> stored =
            lineRepository
                .findAllForTransfer(
                    transfer.getId()
                );

        return sameLines(
            stored,
            request.lines()
        );
    }

    private boolean sameLines(
        List<StockTransferLine> stored,
        List<CreateStockTransferLineRequest> requested
    ) {

        if (
            stored.size()
            != requested.size()
        ) {
            return false;
        }

        Map<UUID, StockTransferLine> byId =
            stored.stream()
                .collect(
                    Collectors.toMap(
                        StockTransferLine::getId,
                        Function.identity()
                    )
                );

        for (
            CreateStockTransferLineRequest requestLine
            : requested
        ) {

            StockTransferLine storedLine =
                byId.get(
                    requestLine.lineId()
                );

            if (storedLine == null) {
                return false;
            }

            if (
                !Objects.equals(
                    storedLine
                        .getStockItem()
                        .getId(),
                    requestLine.stockItemId()
                )
                || !sameDecimal(
                    storedLine.getQuantity(),
                    requestLine.quantity()
                )
                || storedLine.getUnit()
                    != requestLine.unit()
            ) {
                return false;
            }
        }

        return true;
    }

    private void validateLineIdentifiersAvailable(
        UUID transferId,
        List<CreateStockTransferLineRequest> lines
    ) {

        List<UUID> requestedIds =
            lines.stream()
                .map(
                    CreateStockTransferLineRequest::lineId
                )
                .toList();

        List<StockTransferLine> existing =
            lineRepository
                .findAllById(
                    requestedIds
                );

        for (
            StockTransferLine line
            : existing
        ) {

            if (
                !line.getStockTransfer()
                    .getId()
                    .equals(transferId)
            ) {
                throw new InventoryConflictException(
                    "One or more transfer line identifiers are already in use."
                );
            }
        }
    }

    private void validateUniqueLines(
        List<CreateStockTransferLineRequest> lines
    ) {

        HashSet<UUID> lineIds =
            new HashSet<>();

        HashSet<UUID> stockItemIds =
            new HashSet<>();

        for (
            CreateStockTransferLineRequest line
            : lines
        ) {

            if (
                !lineIds.add(
                    line.lineId()
                )
            ) {
                throw new InventoryValidationException(
                    "Transfer line identifiers must be unique."
                );
            }

            if (
                !stockItemIds.add(
                    line.stockItemId()
                )
            ) {
                throw new InventoryValidationException(
                    "A stock item may appear only once in a transfer."
                );
            }
        }
    }

    private Map<UUID, StockItem> resolveStockItems(
        List<CreateStockTransferLineRequest> lines,
        UUID organizationId
    ) {

        List<UUID> itemIds =
            lines.stream()
                .map(
                    CreateStockTransferLineRequest::stockItemId
                )
                .distinct()
                .sorted()
                .toList();

        List<StockItem> stockItems =
            stockItemRepository
                .findAllByIdInAndOrganization_Id(
                    itemIds,
                    organizationId
                );

        if (
            stockItems.size()
            != itemIds.size()
        ) {
            throw new InventoryNotFoundException(
                "One or more stock items do not exist."
            );
        }

        return stockItems
            .stream()
            .collect(
                Collectors.toMap(
                    StockItem::getId,
                    Function.identity()
                )
            );
    }

    private void validateLinesAgainstItems(
        List<CreateStockTransferLineRequest> lines,
        Map<UUID, StockItem> stockItems
    ) {

        for (
            CreateStockTransferLineRequest line
            : lines
        ) {

            StockItem item =
                stockItems.get(
                    line.stockItemId()
                );

            if (
                line.unit()
                    != item.getBaseUnit()
            ) {
                throw new InventoryValidationException(
                    "Transfer line unit must match the stock item base unit."
                );
            }

            if (!isTrackingEnabled(item)) {
                throw new InventoryValidationException(
                    "Stock item subject is not configured to track stock."
                );
            }
        }
    }

    private void saveReplacementLines(
        StockTransfer transfer,
        List<CreateStockTransferLineRequest> requestLines,
        Map<UUID, StockItem> stockItems
    ) {

        for (
            CreateStockTransferLineRequest line
            : requestLines
        ) {

            lineRepository.save(
                new StockTransferLine(
                    line.lineId(),
                    transfer,
                    stockItems.get(
                        line.stockItemId()
                    ),
                    line.quantity(),
                    line.unit()
                )
            );
        }
    }

    private void allocateSourceLots(
        StockItem item,
        StockLocation source,
        InventoryMovement movement,
        BigDecimal requestedQuantity
    ) {

        List<StockLot> lots =
            stockLotRepository
                .findTransferLotsForUpdate(
                    item.getId(),
                    source.getId()
                );

        BigDecimal remaining =
            requestedQuantity;

        for (
            StockLot lot
            : lots
        ) {

            if (remaining.signum() == 0) {
                break;
            }

            BigDecimal allocated =
                lot.getQuantityRemaining()
                    .min(
                        remaining
                    );

            if (
                allocated.signum() <= 0
            ) {
                continue;
            }

            lot.consume(
                allocated
            );

            stockLotRepository.save(
                lot
            );

            movementLotRepository.save(
                new InventoryMovementLot(
                    movement,
                    lot,
                    allocated.negate()
                )
            );

            remaining =
                remaining.subtract(
                    allocated
                );
        }

        if (
            item.isTrackExpiry()
            && remaining.signum() > 0
        ) {
            throw new InventoryConflictException(
                "Expiry-tracked stock is not fully represented by source lots."
            );
        }
    }

    private void recreateDestinationLots(
        StockTransferLine line,
        StockLocation destination,
        InventoryMovement transferIn,
        OffsetDateTime receivedAt
    ) {

        List<InventoryMovementLot> allocations =
            movementLotRepository
                .findAllForMovement(
                    line
                        .getTransferOutMovement()
                        .getId()
                );

        for (
            InventoryMovementLot allocation
            : allocations
        ) {

            StockLot sourceLot =
                allocation.getStockLot();

            BigDecimal quantity =
                allocation
                    .getQuantityDelta()
                    .abs();

            StockLot destinationLot =
                new StockLot(
                    line.getStockItem(),
                    destination,
                    sourceLot.getLotNumber(),
                    sourceLot.getSupplier(),
                    receivedAt,
                    sourceLot.getExpiresAt(),
                    quantity,
                    sourceLot.getUnitCost()
                );

            destinationLot =
                stockLotRepository.save(
                    destinationLot
                );

            movementLotRepository.save(
                new InventoryMovementLot(
                    transferIn,
                    destinationLot,
                    quantity
                )
            );
        }
    }

    private void ensureNoActiveInventorySession(
        UUID stockLocationId,
        String side
    ) {

        if (
            inventorySessionRepository
                .existsByStockLocation_IdAndStatusIn(
                    stockLocationId,
                    ACTIVE_INVENTORY_STATUSES
                )
        ) {
            throw new InventoryConflictException(
                "An active physical inventory session blocks transfer mutation on the "
                    + side
                    + " stock location."
            );
        }
    }

    private BigDecimal currentAvailable(
        UUID stockItemId,
        UUID stockLocationId
    ) {

        return stockBalanceRepository
            .findById(
                new StockBalanceId(
                    stockItemId,
                    stockLocationId
                )
            )
            .map(balance ->
                balance
                    .getPhysicalQuantity()
                    .subtract(
                        balance
                            .getReservedQuantity()
                    )
            )
            .orElse(
                BigDecimal.ZERO
            );
    }

    private StockBalance lockedBalance(
        UUID stockItemId,
        UUID stockLocationId
    ) {

        stockBalanceRepository
            .ensureExists(
                stockItemId,
                stockLocationId
            );

        return stockBalanceRepository
            .findLockedById(
                new StockBalanceId(
                    stockItemId,
                    stockLocationId
                )
            )
            .orElseThrow(() ->
                new InventoryConflictException(
                    "Stock balance could not be locked."
                )
            );
    }

    private List<StockTransferLine> orderedLines(
        UUID transferId
    ) {

        return lineRepository
            .findAllForTransfer(
                transferId
            )
            .stream()
            .sorted(
                Comparator.comparing(
                    line ->
                        line.getStockItem()
                            .getId()
                )
            )
            .toList();
    }

    private StockLocation ownedActiveLocation(
        UUID stockLocationId,
        UUID organizationId
    ) {

        StockLocation stockLocation =
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

        ensureLocationActive(
            stockLocation,
            "Stock"
        );

        return stockLocation;
    }

    private void ensureLocationActive(
        StockLocation location,
        String label
    ) {

        if (!location.isActive()) {
            throw new InventoryValidationException(
                label
                    + " stock location is not active."
            );
        }
    }

    private StockTransfer transferForUpdate(
        UUID transferId,
        UUID organizationId
    ) {

        return transferRepository
            .findOwnedByIdForUpdate(
                transferId,
                organizationId
            )
            .orElseThrow(() ->
                new InventoryNotFoundException(
                    "Stock transfer does not exist."
                )
            );
    }

    private StockTransferMutationResponse
        mutationReplay(
            StockTransfer transfer
        ) {

        return new StockTransferMutationResponse(
            response(transfer),
            true
        );
    }

    private StockTransferMutationResponse
        mutationChanged(
            StockTransfer transfer
        ) {

        return new StockTransferMutationResponse(
            response(transfer),
            false
        );
    }

    private StockTransferResponse response(
        StockTransfer transfer
    ) {

        List<StockTransferLineResponse> lines =
            lineRepository
                .findAllForTransfer(
                    transfer.getId()
                )
                .stream()
                .map(
                    this::lineResponse
                )
                .toList();

        return new StockTransferResponse(
            transfer.getId(),
            transfer
                .getSourceStockLocation()
                .getId(),
            transfer
                .getDestinationStockLocation()
                .getId(),
            transfer.getStatus(),
            transfer.getRequestedBy()
                .getId(),
            transfer.getApprovedBy() == null
                ? null
                : transfer
                    .getApprovedBy()
                    .getId(),
            transfer.getDispatchedBy() == null
                ? null
                : transfer
                    .getDispatchedBy()
                    .getId(),
            transfer.getReceivedBy() == null
                ? null
                : transfer
                    .getReceivedBy()
                    .getId(),
            transfer.getRequestedAt(),
            transfer.getDispatchedAt(),
            transfer.getReceivedAt(),
            transfer.getCancelledAt(),
            transfer.getReason(),
            lines
        );
    }

    private StockTransferLineResponse lineResponse(
        StockTransferLine line
    ) {

        return new StockTransferLineResponse(
            line.getId(),
            line.getStockItem()
                .getId(),
            line.getQuantity(),
            line.getUnit(),
            line.getTransferOutMovement()
                == null
                ? null
                : line
                    .getTransferOutMovement()
                    .getId(),
            line.getTransferInMovement()
                == null
                ? null
                : line
                    .getTransferInMovement()
                    .getId()
        );
    }

    private boolean isTrackingEnabled(
        StockItem stockItem
    ) {

        if (
            stockItem.getProduct()
                != null
        ) {
            return stockItem
                .getProduct()
                .isTrackStock();
        }

        if (
            stockItem.getVariant()
                != null
        ) {
            return stockItem
                .getVariant()
                .getProduct()
                .isTrackStock();
        }

        if (
            stockItem.getIngredient()
                != null
        ) {
            return stockItem
                .getIngredient()
                .isTrackStock();
        }

        return false;
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

    private void lockTransferId(
        UUID transferId
    ) {

        advisoryLock(
            transferId
        );
    }

    private void lockStockLocation(
        UUID stockLocationId
    ) {

        advisoryLock(
            stockLocationId
        );
    }

    private void advisoryLock(
        UUID identifier
    ) {

        long lockKey =
            identifier
                .getMostSignificantBits()
            ^ identifier
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

    private boolean sameDecimal(
        BigDecimal left,
        BigDecimal right
    ) {

        return left.compareTo(
            right
        ) == 0;
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