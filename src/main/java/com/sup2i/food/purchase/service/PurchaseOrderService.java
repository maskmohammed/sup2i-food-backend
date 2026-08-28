package com.sup2i.food.purchase.service;

import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.catalog.repository.IngredientRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.catalog.repository.ProductVariantRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.inventory.domain.InventoryMovement;
import com.sup2i.food.inventory.domain.InventoryMovementLot;
import com.sup2i.food.inventory.domain.InventoryMovementType;
import com.sup2i.food.inventory.domain.StockBalance;
import com.sup2i.food.inventory.domain.StockBalanceId;
import com.sup2i.food.inventory.domain.StockItem;
import com.sup2i.food.inventory.domain.StockLocation;
import com.sup2i.food.inventory.domain.StockLot;
import com.sup2i.food.inventory.repository.InventoryMovementLotRepository;
import com.sup2i.food.inventory.repository.InventoryMovementRepository;
import com.sup2i.food.inventory.repository.StockBalanceRepository;
import com.sup2i.food.inventory.repository.StockItemRepository;
import com.sup2i.food.inventory.repository.StockLocationRepository;
import com.sup2i.food.inventory.repository.StockLotRepository;
import com.sup2i.food.organization.domain.Campus;
import com.sup2i.food.organization.repository.CampusRepository;
import com.sup2i.food.procurement.domain.Supplier;
import com.sup2i.food.procurement.repository.SupplierRepository;
import com.sup2i.food.purchase.api.dto.CreatePurchaseOrderLineRequest;
import com.sup2i.food.purchase.api.dto.CreatePurchaseOrderRequest;
import com.sup2i.food.purchase.api.dto.PurchaseOrderHistoryElement;
import com.sup2i.food.purchase.api.dto.PurchaseOrderReceiptElement;
import com.sup2i.food.purchase.api.dto.PurchaseOrderResponse;
import com.sup2i.food.purchase.api.dto.ReceivePurchaseOrderItemRequest;
import com.sup2i.food.purchase.api.dto.ReceivePurchaseOrderRequest;
import com.sup2i.food.purchase.api.dto.UpdatePurchaseOrderRequest;
import com.sup2i.food.purchase.domain.PurchaseOrder;
import com.sup2i.food.purchase.domain.PurchaseOrderCalculator;
import com.sup2i.food.purchase.domain.PurchaseOrderHistory;
import com.sup2i.food.purchase.domain.PurchaseOrderHistoryEvent;
import com.sup2i.food.purchase.domain.PurchaseOrderLine;
import com.sup2i.food.purchase.domain.PurchaseOrderPolicy;
import com.sup2i.food.purchase.domain.PurchaseOrderReceipt;
import com.sup2i.food.purchase.domain.PurchaseOrderReceiptLine;
import com.sup2i.food.purchase.domain.PurchaseOrderStatus;
import com.sup2i.food.purchase.exception.PurchaseConflictException;
import com.sup2i.food.purchase.exception.PurchaseNotFoundException;
import com.sup2i.food.purchase.exception.PurchaseValidationException;
import com.sup2i.food.purchase.repository.PurchaseOrderHistoryRepository;
import com.sup2i.food.purchase.repository.PurchaseOrderReceiptRepository;
import com.sup2i.food.purchase.repository.PurchaseOrderRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PurchaseOrderService {

    private static final String
        REFERENCE_TYPE =
            "PURCHASE_ORDER";

    private static final DateTimeFormatter
        REFERENCE_DATE =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;
    private final CampusRepository campusRepository;
    private final StockLocationRepository stockLocationRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final IngredientRepository ingredientRepository;
    private final StockItemRepository stockItemRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryMovementLotRepository movementLotRepository;
    private final StockLotRepository stockLotRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderHistoryRepository historyRepository;
    private final PurchaseOrderReceiptRepository receiptRepository;

    public PurchaseOrderService(
        UserRepository userRepository,
        SupplierRepository supplierRepository,
        CampusRepository campusRepository,
        StockLocationRepository stockLocationRepository,
        ProductRepository productRepository,
        ProductVariantRepository variantRepository,
        IngredientRepository ingredientRepository,
        StockItemRepository stockItemRepository,
        StockBalanceRepository stockBalanceRepository,
        InventoryMovementRepository movementRepository,
        InventoryMovementLotRepository movementLotRepository,
        StockLotRepository stockLotRepository,
        PurchaseOrderRepository purchaseOrderRepository,
        PurchaseOrderHistoryRepository historyRepository,
        PurchaseOrderReceiptRepository receiptRepository
    ) {
        this.userRepository = userRepository;
        this.supplierRepository = supplierRepository;
        this.campusRepository = campusRepository;
        this.stockLocationRepository = stockLocationRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.ingredientRepository = ingredientRepository;
        this.stockItemRepository = stockItemRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.movementRepository = movementRepository;
        this.movementLotRepository = movementLotRepository;
        this.stockLotRepository = stockLotRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.historyRepository = historyRepository;
        this.receiptRepository = receiptRepository;
    }

    @Transactional
    public PurchaseOrderResponse create(
        UUID actorId,
        CreatePurchaseOrderRequest request
    ) {
        User actor = requiredUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        Supplier supplier =
            supplierRepository
                .findByIdAndOrganization_IdAndActiveTrue(
                    request.supplierId(),
                    organizationId
                )
                .orElseThrow(() ->
                    new PurchaseNotFoundException(
                        "Supplier does not exist or is not active."
                    )
                );

        Campus campus =
            campusRepository
                .findByIdAndOrganization_Id(
                    request.campusId(),
                    organizationId
                )
                .orElseThrow(() ->
                    new PurchaseNotFoundException(
                        "Campus does not exist."
                    )
                );

        PurchaseOrder purchaseOrder =
            new PurchaseOrder(
                actor.getOrganization(),
                supplier,
                campus,
                buildReference(),
                normalizeNullableText(
                    request.notes()
                ),
                actor
            );

        attachLines(
            purchaseOrder,
            request.lines(),
            organizationId
        );

        purchaseOrder =
            purchaseOrderRepository
                .save(purchaseOrder);

        historyRepository
            .save(
                new PurchaseOrderHistory(
                    purchaseOrder,
                    PurchaseOrderHistoryEvent.CREATED,
                    null,
                    PurchaseOrderStatus.DRAFT,
                    actor,
                    "Purchase order created."
                )
            );

        return response(purchaseOrder);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> findAll(
        UUID actorId
    ) {
        UUID organizationId =
            requiredUser(actorId)
                .getOrganization()
                .getId();

        return purchaseOrderRepository
            .findAllByOrganization_IdOrderByCreatedAtDesc(
                organizationId
            )
            .stream()
            .map(this::response)
            .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse find(
        UUID actorId,
        UUID purchaseOrderId
    ) {
        UUID organizationId =
            requiredUser(actorId)
                .getOrganization()
                .getId();

        return response(
            requiredOrder(
                purchaseOrderId,
                organizationId
            )
        );
    }

    @Transactional
    public PurchaseOrderResponse update(
        UUID actorId,
        UUID purchaseOrderId,
        UpdatePurchaseOrderRequest request
    ) {
        User actor = requiredUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        PurchaseOrder purchaseOrder =
            lockedOrder(
                purchaseOrderId,
                organizationId
            );

        if (
            !PurchaseOrderPolicy.canEdit(
                purchaseOrder.getStatus()
            )
        ) {
            throw new PurchaseConflictException(
                "Only draft purchase orders can be edited."
            );
        }

        purchaseOrder.clearLines();

        attachLines(
            purchaseOrder,
            request.lines(),
            organizationId
        );

        purchaseOrder.updateNotes(
            normalizeNullableText(
                request.notes()
            )
        );

        purchaseOrderRepository
            .save(purchaseOrder);

        historyRepository
            .save(
                new PurchaseOrderHistory(
                    purchaseOrder,
                    PurchaseOrderHistoryEvent.UPDATED,
                    PurchaseOrderStatus.DRAFT,
                    PurchaseOrderStatus.DRAFT,
                    actor,
                    "Purchase order lines updated."
                )
            );

        return response(purchaseOrder);
    }

    @Transactional
    public PurchaseOrderResponse send(
        UUID actorId,
        UUID purchaseOrderId
    ) {
        User actor = requiredUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        PurchaseOrder purchaseOrder =
            lockedOrder(
                purchaseOrderId,
                organizationId
            );

        PurchaseOrderStatus from =
            purchaseOrder.getStatus();

        if (
            !PurchaseOrderPolicy.canSend(from)
        ) {
            throw new PurchaseConflictException(
                "Only draft purchase orders can be sent."
            );
        }

        purchaseOrder.updateStatus(
            PurchaseOrderStatus.SENT
        );

        purchaseOrderRepository
            .save(purchaseOrder);

        historyRepository
            .save(
                new PurchaseOrderHistory(
                    purchaseOrder,
                    PurchaseOrderHistoryEvent.SENT,
                    from,
                    PurchaseOrderStatus.SENT,
                    actor,
                    null
                )
            );

        return response(purchaseOrder);
    }

    @Transactional
    public PurchaseOrderResponse confirm(
        UUID actorId,
        UUID purchaseOrderId
    ) {
        User actor = requiredUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        PurchaseOrder purchaseOrder =
            lockedOrder(
                purchaseOrderId,
                organizationId
            );

        PurchaseOrderStatus from =
            purchaseOrder.getStatus();

        if (
            !PurchaseOrderPolicy.canConfirm(from)
        ) {
            throw new PurchaseConflictException(
                "Only sent purchase orders can be confirmed."
            );
        }

        purchaseOrder.updateStatus(
            PurchaseOrderStatus.CONFIRMED
        );

        purchaseOrderRepository
            .save(purchaseOrder);

        historyRepository
            .save(
                new PurchaseOrderHistory(
                    purchaseOrder,
                    PurchaseOrderHistoryEvent.CONFIRMED,
                    from,
                    PurchaseOrderStatus.CONFIRMED,
                    actor,
                    null
                )
            );

        return response(purchaseOrder);
    }

    @Transactional
    public PurchaseOrderResponse receive(
        UUID actorId,
        UUID purchaseOrderId,
        ReceivePurchaseOrderRequest request
    ) {
        User actor = requiredUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        PurchaseOrder purchaseOrder =
            lockedOrder(
                purchaseOrderId,
                organizationId
            );

        PurchaseOrderStatus from =
            purchaseOrder.getStatus();

        if (
            !PurchaseOrderPolicy.canReceive(
                from
            )
        ) {
            throw new PurchaseConflictException(
                "Purchase order cannot receive stock in its current status."
            );
        }

        StockLocation stockLocation =
            stockLocationRepository
                .findByIdAndLocation_Campus_Organization_Id(
                    request.stockLocationId(),
                    organizationId
                )
                .orElseThrow(() ->
                    new PurchaseNotFoundException(
                        "Stock location does not exist."
                    )
                );

        PurchaseOrderReceipt receipt =
            new PurchaseOrderReceipt(
                purchaseOrder,
                stockLocation,
                actor,
                normalizeNullableText(
                    request.notes()
                )
            );

        for (
            ReceivePurchaseOrderItemRequest item
            : request.items()
        ) {
            receiveLine(
                actor,
                purchaseOrder,
                stockLocation,
                receipt,
                item
            );
        }

        PurchaseOrderStatus after =
            allReceived(purchaseOrder)
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;

        purchaseOrder.updateStatus(after);

        receiptRepository
            .save(receipt);

        purchaseOrderRepository
            .save(purchaseOrder);

        historyRepository
            .save(
                new PurchaseOrderHistory(
                    purchaseOrder,
                    after == PurchaseOrderStatus.RECEIVED
                        ? PurchaseOrderHistoryEvent.RECEIVED
                        : PurchaseOrderHistoryEvent.PARTIALLY_RECEIVED,
                    from,
                    after,
                    actor,
                    null
                )
            );

        return response(purchaseOrder);
    }

    @Transactional
    public PurchaseOrderResponse cancel(
        UUID actorId,
        UUID purchaseOrderId
    ) {
        User actor = requiredUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        PurchaseOrder purchaseOrder =
            lockedOrder(
                purchaseOrderId,
                organizationId
            );

        PurchaseOrderStatus from =
            purchaseOrder.getStatus();

        if (
            !PurchaseOrderPolicy.canCancel(from)
        ) {
            throw new PurchaseConflictException(
                "Confirmed purchase orders cannot be cancelled."
            );
        }

        purchaseOrder.updateStatus(
            PurchaseOrderStatus.CANCELLED
        );

        purchaseOrderRepository
            .save(purchaseOrder);

        historyRepository
            .save(
                new PurchaseOrderHistory(
                    purchaseOrder,
                    PurchaseOrderHistoryEvent.CANCELLED,
                    from,
                    PurchaseOrderStatus.CANCELLED,
                    actor,
                    null
                )
            );

        return response(purchaseOrder);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderHistoryElement> history(
        UUID actorId,
        UUID purchaseOrderId
    ) {
        UUID organizationId =
            requiredUser(actorId)
                .getOrganization()
                .getId();

        requiredOrder(
            purchaseOrderId,
            organizationId
        );

        return historyRepository
            .findAllByPurchaseOrder_IdOrderByOccurredAtDesc(
                purchaseOrderId
            )
            .stream()
            .map(PurchaseOrderHistoryElement::from)
            .toList();
    }

    private void receiveLine(
        User actor,
        PurchaseOrder purchaseOrder,
        StockLocation stockLocation,
        PurchaseOrderReceipt receipt,
        ReceivePurchaseOrderItemRequest item
    ) {
        PurchaseOrderLine line =
            purchaseOrder
                .getLines()
                .stream()
                .filter(candidate ->
                    candidate.getId()
                        .equals(
                            item.purchaseOrderLineId()
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                    new PurchaseNotFoundException(
                        "Purchase order line does not exist in this order."
                    )
                );

        if (
            item.quantity()
                .compareTo(
                    line.remainingQuantity()
                )
            > 0
        ) {
            throw new PurchaseConflictException(
                "Received quantity exceeds the remaining ordered quantity."
            );
        }

        UUID organizationId =
            purchaseOrder.getOrganization()
                .getId();

        StockItem stockItem =
            resolveLineStockItem(
                organizationId,
                line
            );

        if (stockItem == null) {
            throw new PurchaseValidationException(
                "Purchase order line has no stock item to receive into."
            );
        }

        StockBalanceId balanceId =
            new StockBalanceId(
                stockItem.getId(),
                stockLocation.getId()
            );

        stockBalanceRepository
            .ensureExists(
                stockItem.getId(),
                stockLocation.getId()
            );

        StockBalance balance =
            stockBalanceRepository
                .findLockedById(
                    balanceId
                )
                .orElseThrow(() ->
                    new PurchaseConflictException(
                        "Stock balance could not be locked."
                    )
                );

        balance.applyPhysicalDelta(
            item.quantity()
        );

        BigDecimal movementUnitCost =
            scaleMoney(
                item.unitCost()
            );

        InventoryMovement movement =
            new InventoryMovement(
                stockItem,
                stockLocation,
                InventoryMovementType.PURCHASE_IN,
                item.quantity(),
                BigDecimal.ZERO,
                line.getUnit(),
                movementUnitCost,
                REFERENCE_TYPE,
                purchaseOrder.getId(),
                "Purchase order line receipt",
                normalizeNullableText(
                    purchaseOrder.getReference()
                ),
                actor
            );

        movement =
            movementRepository
                .save(movement);

        stockBalanceRepository
            .save(balance);

        StockLot lot = null;

        if (
            item.lotNumber() != null
            || item.expiresAt() != null
        ) {
            lot =
                createLot(
                    actor,
                    stockItem,
                    stockLocation,
                    purchaseOrder,
                    item
                );

            movementLotRepository
                .save(
                    new InventoryMovementLot(
                        movement,
                        lot,
                        item.quantity()
                    )
                );
        }

        line.applyReceived(
            item.quantity()
        );

        PurchaseOrderReceiptLine receiptLine =
            new PurchaseOrderReceiptLine(
                receipt,
                line,
                item.quantity(),
                line.getUnit(),
                item.unitCost(),
                normalizeNullableText(
                    item.lotNumber()
                ),
                item.expiresAt()
            );

        receiptLine.attachInventoryMovement(
            movement
        );

        if (lot != null) {
            receiptLine.attachGeneratedLot(
                lot
            );
        }

        receipt.addLine(receiptLine);
    }

    private StockLot createLot(
        User actor,
        StockItem stockItem,
        StockLocation stockLocation,
        PurchaseOrder purchaseOrder,
        ReceivePurchaseOrderItemRequest item
    ) {
        StockLot lot =
            new StockLot(
                stockItem,
                stockLocation,
                normalizeNullableText(
                    item.lotNumber()
                ),
                purchaseOrder.getSupplier(),
                OffsetDateTime.now(),
                item.expiresAt(),
                item.quantity(),
                scaleMoney(
                    item.unitCost()
                )
            );

        return stockLotRepository
            .save(lot);
    }

    private void attachLines(
        PurchaseOrder purchaseOrder,
        List<CreatePurchaseOrderLineRequest> lineRequests,
        UUID organizationId
    ) {
        List<BigDecimal> lineTotals =
            new ArrayList<>();

        for (
            CreatePurchaseOrderLineRequest request
            : lineRequests
        ) {
            validateLineSubject(request);

            PurchaseOrderLine line =
                new PurchaseOrderLine(
                    purchaseOrder,
                    resolveProduct(
                        request.productId(),
                        organizationId
                    ),
                    resolveVariant(
                        request.variantId(),
                        organizationId
                    ),
                    resolveIngredient(
                        request.ingredientId(),
                        organizationId
                    ),
                    request.quantity(),
                    request.unit(),
                    request.unitPrice(),
                    PurchaseOrderCalculator.lineTotal(
                        request.quantity(),
                        request.unitPrice()
                    )
                );

            purchaseOrder.addLine(line);

            lineTotals.add(
                line.getLineTotal()
            );
        }

        purchaseOrder.setTotalEstimated(
            PurchaseOrderCalculator.orderTotal(
                lineTotals
            )
        );
    }

    private void validateLineSubject(
        CreatePurchaseOrderLineRequest request
    ) {
        int subjects = 0;

        if (request.productId() != null) {
            subjects++;
        }

        if (request.variantId() != null) {
            subjects++;
        }

        if (request.ingredientId() != null) {
            subjects++;
        }

        if (subjects != 1) {
            throw new PurchaseValidationException(
                "Exactly one line subject (product, variant or ingredient) is required."
            );
        }
    }

    private Product resolveProduct(
        UUID productId,
        UUID organizationId
    ) {
        if (productId == null) {
            return null;
        }

        return productRepository
            .findCatalogProduct(
                productId,
                organizationId
            )
            .orElseThrow(() ->
                new PurchaseValidationException(
                    "Line product does not exist in this organization."
                )
            );
    }

    private ProductVariant resolveVariant(
        UUID variantId,
        UUID organizationId
    ) {
        if (variantId == null) {
            return null;
        }

        return variantRepository
            .findByIdAndProduct_Organization_Id(
                variantId,
                organizationId
            )
            .orElseThrow(() ->
                new PurchaseValidationException(
                    "Line variant does not exist in this organization."
                )
            );
    }

    private Ingredient resolveIngredient(
        UUID ingredientId,
        UUID organizationId
    ) {
        if (ingredientId == null) {
            return null;
        }

        return ingredientRepository
            .findByIdAndOrganization_Id(
                ingredientId,
                organizationId
            )
            .orElseThrow(() ->
                new PurchaseValidationException(
                    "Line ingredient does not exist in this organization."
                )
            );
    }

    private StockItem resolveLineStockItem(
        UUID organizationId,
        PurchaseOrderLine line
    ) {
        if (line.getIngredient() != null) {
            return stockItemRepository
                .findByOrganization_IdAndIngredient_Id(
                    organizationId,
                    line.getIngredient()
                        .getId()
                )
                .orElse(null);
        }

        if (line.getVariant() != null) {
            return stockItemRepository
                .findByOrganization_IdAndVariant_Id(
                    organizationId,
                    line.getVariant()
                        .getId()
                )
                .orElse(null);
        }

        if (line.getProduct() != null) {
            return stockItemRepository
                .findByOrganization_IdAndProduct_Id(
                    organizationId,
                    line.getProduct()
                        .getId()
                )
                .orElse(null);
        }

        return null;
    }

    private boolean allReceived(
        PurchaseOrder purchaseOrder
    ) {
        for (
            PurchaseOrderLine line
            : purchaseOrder.getLines()
        ) {
            if (line.hasRemaining()) {
                return false;
            }
        }

        return true;
    }

    private PurchaseOrder requiredOrder(
        UUID purchaseOrderId,
        UUID organizationId
    ) {
        return purchaseOrderRepository
            .findByIdAndOrganization_Id(
                purchaseOrderId,
                organizationId
            )
            .orElseThrow(() ->
                new PurchaseNotFoundException(
                    "Purchase order does not exist."
                )
            );
    }

    private PurchaseOrder lockedOrder(
        UUID purchaseOrderId,
        UUID organizationId
    ) {
        return purchaseOrderRepository
            .findOwnedByIdForUpdate(
                purchaseOrderId,
                organizationId
            )
            .orElseThrow(() ->
                new PurchaseNotFoundException(
                    "Purchase order does not exist."
                )
            );
    }

    private PurchaseOrderResponse response(
        PurchaseOrder purchaseOrder
    ) {
        List<PurchaseOrderHistoryElement>
            history =
                historyRepository
                    .findAllByPurchaseOrder_IdOrderByOccurredAtDesc(
                        purchaseOrder.getId()
                    )
                    .stream()
                    .map(
                        PurchaseOrderHistoryElement
                            ::from
                    )
                    .toList();

        List<PurchaseOrderReceiptElement>
            receipts =
                receiptRepository
                    .findAllByPurchaseOrder_IdOrderByReceivedAtDesc(
                        purchaseOrder.getId()
                    )
                    .stream()
                    .map(
                        PurchaseOrderReceiptElement
                            ::from
                    )
                    .toList();

        return PurchaseOrderResponse.from(
            purchaseOrder,
            history,
            receipts
        );
    }

    private BigDecimal scaleMoney(
        BigDecimal value
    ) {
        if (value == null) {
            return null;
        }

        return value.setScale(
            2,
            RoundingMode.HALF_UP
        );
    }

    private String buildReference() {
        String suffix =
            UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();

        return "PO-"
            + LocalDate.now()
                .format(REFERENCE_DATE)
            + "-"
            + suffix;
    }

    private User requiredUser(
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

    private String normalizeNullableText(
        String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }
}