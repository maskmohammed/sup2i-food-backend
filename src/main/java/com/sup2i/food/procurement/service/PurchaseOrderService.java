package com.sup2i.food.procurement.service;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.inventory.api.dto.ReceiveStockLineRequest;
import com.sup2i.food.inventory.api.dto.ReceiveStockRequest;
import com.sup2i.food.inventory.service.InventoryReceiptService;
import com.sup2i.food.procurement.api.dto.PurchaseOrderCommand;
import com.sup2i.food.procurement.api.dto.PurchaseOrderItemCommand;
import com.sup2i.food.procurement.api.dto.PurchaseOrderItemResponse;
import com.sup2i.food.procurement.api.dto.PurchaseOrderReceiptCommand;
import com.sup2i.food.procurement.api.dto.PurchaseOrderReceiptLineCommand;
import com.sup2i.food.procurement.api.dto.PurchaseOrderResponse;
import com.sup2i.food.procurement.domain.PurchaseOrderStatus;
import com.sup2i.food.procurement.exception.ProcurementConflictException;
import com.sup2i.food.procurement.exception.ProcurementNotFoundException;
import com.sup2i.food.procurement.exception.ProcurementValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PurchaseOrderService {

    private static final BigDecimal ZERO =
        new BigDecimal("0.000");

    private final JdbcTemplate jdbcTemplate;

    private final InventoryReceiptService
        inventoryReceiptService;

    public PurchaseOrderService(
        JdbcTemplate jdbcTemplate,
        InventoryReceiptService inventoryReceiptService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.inventoryReceiptService =
            inventoryReceiptService;
    }

    @Transactional
    public PurchaseOrderResponse create(
        UUID actorId,
        UUID purchaseOrderId,
        PurchaseOrderCommand command
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            purchaseOrderId,
            "Purchase order id"
        );

        validateCreateCommand(
            command
        );

        PurchaseOrderHeader existing =
            findOwnedHeader(
                organizationId,
                purchaseOrderId,
                false
            );

        if (existing != null) {

            PurchaseOrderResponse stored =
                response(
                    existing
                );

            if (
                sameCreatePayload(
                    stored,
                    command
                )
            ) {
                return stored;
            }

            throw new ProcurementConflictException(
                "Purchase order identifier is already used by another payload."
            );
        }

        validateSupplier(
            organizationId,
            command.supplierId()
        );

        validateCampus(
            organizationId,
            command.campusId()
        );

        for (
            PurchaseOrderItemCommand item
            : command.items()
        ) {
            validateItem(
                organizationId,
                item
            );
        }

        String reference =
            requiredText(
                command.reference(),
                "Purchase order reference",
                80
            );

        BigDecimal totalEstimated =
            nonNegativeNullable(
                command.totalEstimated(),
                "Purchase order total estimated"
            );

        try {

            jdbcTemplate.update(
                """
                INSERT INTO purchase_orders(
                    id,
                    supplier_id,
                    campus_id,
                    reference,
                    status,
                    total_estimated,
                    created_by
                )
                VALUES (?, ?, ?, ?, 'DRAFT', ?, ?)
                """,
                purchaseOrderId,
                command.supplierId(),
                command.campusId(),
                reference,
                totalEstimated,
                actorId
            );

            for (
                PurchaseOrderItemCommand item
                : command.items()
            ) {

                jdbcTemplate.update(
                    """
                    INSERT INTO purchase_order_items(
                        id,
                        purchase_order_id,
                        stock_item_id,
                        quantity,
                        unit,
                        unit_price
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    purchaseOrderId,
                    item.stockItemId(),
                    item.quantity(),
                    item.unit().name(),
                    item.unitPrice()
                );
            }

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new ProcurementConflictException(
                "Purchase order violates a procurement invariant."
            );
        }

        return get(
            actorId,
            purchaseOrderId
        );
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(
        UUID actorId,
        UUID purchaseOrderId
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            purchaseOrderId,
            "Purchase order id"
        );

        PurchaseOrderHeader header =
            requireOwnedHeader(
                organizationId,
                purchaseOrderId,
                false
            );

        return response(
            header
        );
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> list(
        UUID actorId,
        PurchaseOrderStatus status
    ) {

        UUID organizationId =
            organizationId(actorId);

        List<PurchaseOrderHeader> headers;

        if (status == null) {

            headers =
                jdbcTemplate.query(
                    """
                    SELECT
                        po.id,
                        po.supplier_id,
                        po.campus_id,
                        po.reference,
                        po.status,
                        po.total_estimated,
                        po.created_by,
                        po.approved_by,
                        po.created_at,
                        po.updated_at
                    FROM purchase_orders po
                    JOIN suppliers s
                      ON s.id = po.supplier_id
                    JOIN campuses c
                      ON c.id = po.campus_id
                    WHERE s.organization_id = ?
                      AND c.organization_id = ?
                    ORDER BY po.created_at DESC, po.id
                    """,
                    (resultSet, rowNumber) ->
                        header(
                            resultSet
                        ),
                    organizationId,
                    organizationId
                );

        } else {

            headers =
                jdbcTemplate.query(
                    """
                    SELECT
                        po.id,
                        po.supplier_id,
                        po.campus_id,
                        po.reference,
                        po.status,
                        po.total_estimated,
                        po.created_by,
                        po.approved_by,
                        po.created_at,
                        po.updated_at
                    FROM purchase_orders po
                    JOIN suppliers s
                      ON s.id = po.supplier_id
                    JOIN campuses c
                      ON c.id = po.campus_id
                    WHERE s.organization_id = ?
                      AND c.organization_id = ?
                      AND po.status = ?
                    ORDER BY po.created_at DESC, po.id
                    """,
                    (resultSet, rowNumber) ->
                        header(
                            resultSet
                        ),
                    organizationId,
                    organizationId,
                    status.name()
                );
        }

        List<PurchaseOrderResponse> responses =
            new ArrayList<>();

        for (
            PurchaseOrderHeader header
            : headers
        ) {
            responses.add(
                response(
                    header
                )
            );
        }

        return List.copyOf(
            responses
        );
    }

    @Transactional
    public PurchaseOrderResponse submit(
        UUID actorId,
        UUID purchaseOrderId
    ) {

        UUID organizationId =
            organizationId(actorId);

        PurchaseOrderHeader header =
            requireOwnedHeader(
                organizationId,
                purchaseOrderId,
                true
            );

        if (
            header.status()
                == PurchaseOrderStatus.DRAFT
        ) {

            updateStatus(
                header,
                PurchaseOrderStatus.SUBMITTED
            );

            return response(
                requireOwnedHeader(
                    organizationId,
                    purchaseOrderId,
                    false
                )
            );
        }

        if (
            header.status()
                == PurchaseOrderStatus.SUBMITTED
            || header.status()
                == PurchaseOrderStatus.APPROVED
            || header.status()
                == PurchaseOrderStatus.ORDERED
            || header.status()
                == PurchaseOrderStatus.PARTIALLY_RECEIVED
            || header.status()
                == PurchaseOrderStatus.RECEIVED
        ) {
            return response(
                header
            );
        }

        throw new ProcurementConflictException(
            "Cancelled purchase order cannot be submitted."
        );
    }

    @Transactional
    public PurchaseOrderResponse approve(
        UUID actorId,
        UUID purchaseOrderId
    ) {

        UUID organizationId =
            organizationId(actorId);

        PurchaseOrderHeader header =
            requireOwnedHeader(
                organizationId,
                purchaseOrderId,
                true
            );

        if (
            header.status()
                == PurchaseOrderStatus.SUBMITTED
        ) {

            int updated =
                jdbcTemplate.update(
                    """
                    UPDATE purchase_orders
                    SET
                        status = 'APPROVED',
                        approved_by = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                      AND status = 'SUBMITTED'
                    """,
                    actorId,
                    purchaseOrderId
                );

            if (updated != 1) {
                throw new ProcurementConflictException(
                    "Purchase order changed concurrently while approving."
                );
            }

            return response(
                requireOwnedHeader(
                    organizationId,
                    purchaseOrderId,
                    false
                )
            );
        }

        if (
            header.status()
                == PurchaseOrderStatus.APPROVED
            || header.status()
                == PurchaseOrderStatus.ORDERED
            || header.status()
                == PurchaseOrderStatus.PARTIALLY_RECEIVED
            || header.status()
                == PurchaseOrderStatus.RECEIVED
        ) {
            return response(
                header
            );
        }

        throw new ProcurementConflictException(
            "Only a submitted purchase order can be approved."
        );
    }

    @Transactional
    public PurchaseOrderResponse markOrdered(
        UUID actorId,
        UUID purchaseOrderId
    ) {

        UUID organizationId =
            organizationId(actorId);

        PurchaseOrderHeader header =
            requireOwnedHeader(
                organizationId,
                purchaseOrderId,
                true
            );

        if (
            header.status()
                == PurchaseOrderStatus.APPROVED
        ) {

            updateStatus(
                header,
                PurchaseOrderStatus.ORDERED
            );

            return response(
                requireOwnedHeader(
                    organizationId,
                    purchaseOrderId,
                    false
                )
            );
        }

        if (
            header.status()
                == PurchaseOrderStatus.ORDERED
            || header.status()
                == PurchaseOrderStatus.PARTIALLY_RECEIVED
            || header.status()
                == PurchaseOrderStatus.RECEIVED
        ) {
            return response(
                header
            );
        }

        throw new ProcurementConflictException(
            "Only an approved purchase order can be marked ordered."
        );
    }

    @Transactional
    public PurchaseOrderResponse cancel(
        UUID actorId,
        UUID purchaseOrderId
    ) {

        UUID organizationId =
            organizationId(actorId);

        PurchaseOrderHeader header =
            requireOwnedHeader(
                organizationId,
                purchaseOrderId,
                true
            );

        if (
            header.status()
                == PurchaseOrderStatus.CANCELLED
        ) {
            return response(
                header
            );
        }

        if (
            header.status()
                == PurchaseOrderStatus.ORDERED
            || header.status()
                == PurchaseOrderStatus.PARTIALLY_RECEIVED
            || header.status()
                == PurchaseOrderStatus.RECEIVED
        ) {
            throw new ProcurementConflictException(
                "Ordered or received purchase order requires an explicit stock return or reversal workflow before cancellation."
            );
        }

        updateStatus(
            header,
            PurchaseOrderStatus.CANCELLED
        );

        return response(
            requireOwnedHeader(
                organizationId,
                purchaseOrderId,
                false
            )
        );
    }

    @Transactional
    public PurchaseOrderResponse receive(
        UUID actorId,
        UUID purchaseOrderId,
        PurchaseOrderReceiptCommand command
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            purchaseOrderId,
            "Purchase order id"
        );

        validateReceiptCommand(
            command
        );

        PurchaseOrderHeader header =
            requireOwnedHeader(
                organizationId,
                purchaseOrderId,
                true
            );

        List<PurchaseOrderItemResponse> items =
            loadItems(
                purchaseOrderId
            );

        ExistingReceipt existingReceipt =
            existingReceipt(
                organizationId,
                command.receiptId()
            );

        if (existingReceipt != null) {

            verifyReceiptReplay(
                header,
                command,
                existingReceipt
            );

            return response(
                header
            );
        }

        if (
            header.status()
                != PurchaseOrderStatus.ORDERED
            && header.status()
                != PurchaseOrderStatus.PARTIALLY_RECEIVED
        ) {
            throw new ProcurementConflictException(
                "Only an ordered or partially received purchase order can receive stock."
            );
        }

        validateStockLocationCampus(
            organizationId,
            header.campusId(),
            command.stockLocationId()
        );

        Map<UUID, PurchaseOrderItemResponse>
            itemsById =
                new HashMap<>();

        for (
            PurchaseOrderItemResponse item
            : items
        ) {
            itemsById.put(
                item.id(),
                item
            );
        }

        HashSet<UUID> lineIds =
            new HashSet<>();

        Map<UUID, BigDecimal>
            incomingByItem =
                new HashMap<>();

        List<ReceiveStockLineRequest>
            inventoryLines =
                new ArrayList<>();

        for (
            PurchaseOrderReceiptLineCommand line
            : command.lines()
        ) {

            requireId(
                line.lineId(),
                "Receipt line id"
            );

            requireId(
                line.purchaseOrderItemId(),
                "Purchase order item id"
            );

            if (
                !lineIds.add(
                    line.lineId()
                )
            ) {
                throw new ProcurementValidationException(
                    "Receipt line identifiers must be unique."
                );
            }

            positive(
                line.quantity(),
                "Receipt quantity"
            );

            nonNegativeNullable(
                line.unitCost(),
                "Receipt unit cost"
            );

            nullableText(
                line.lotNumber(),
                120,
                "Lot number"
            );

            PurchaseOrderItemResponse item =
                itemsById.get(
                    line.purchaseOrderItemId()
                );

            if (item == null) {
                throw new ProcurementNotFoundException(
                    "Purchase order item does not exist."
                );
            }

            BigDecimal currentIncoming =
                incomingByItem.getOrDefault(
                    item.id(),
                    ZERO
                );

            incomingByItem.put(
                item.id(),
                currentIncoming.add(
                    line.quantity()
                )
            );

            inventoryLines.add(
                new ReceiveStockLineRequest(
                    line.lineId(),
                    item.stockItemId(),
                    line.quantity(),
                    item.unit(),
                    line.unitCost(),
                    nullableText(
                        line.lotNumber()
                    ),
                    line.expiresAt()
                )
            );
        }

        for (
            Map.Entry<UUID, BigDecimal> entry
            : incomingByItem.entrySet()
        ) {

            PurchaseOrderItemResponse item =
                itemsById.get(
                    entry.getKey()
                );

            BigDecimal resulting =
                item.receivedQuantity()
                    .add(
                        entry.getValue()
                    );

            if (
                resulting.compareTo(
                    item.quantity()
                ) > 0
            ) {
                throw new ProcurementConflictException(
                    "Purchase order receipt exceeds ordered quantity."
                );
            }
        }

        ReceiveStockRequest inventoryRequest =
            new ReceiveStockRequest(
                command.stockLocationId(),
                header.supplierId(),
                nullableText(
                    command.receiptReference(),
                    100,
                    "Receipt reference"
                ),
                nullableText(
                    command.notes()
                ),
                List.copyOf(
                    inventoryLines
                )
            );

        inventoryReceiptService.receive(
            actorId,
            command.receiptId(),
            inventoryRequest
        );

        int receiptLinked =
            jdbcTemplate.update(
                """
                UPDATE stock_receipts
                SET purchase_order_id = ?
                WHERE id = ?
                  AND purchase_order_id IS NULL
                """,
                purchaseOrderId,
                command.receiptId()
            );

        if (receiptLinked != 1) {
            throw new ProcurementConflictException(
                "Stock receipt could not be linked to purchase order."
            );
        }

        for (
            PurchaseOrderReceiptLineCommand line
            : command.lines()
        ) {

            int lineLinked =
                jdbcTemplate.update(
                    """
                    UPDATE stock_receipt_lines
                    SET purchase_order_item_id = ?
                    WHERE id = ?
                      AND stock_receipt_id = ?
                      AND purchase_order_item_id IS NULL
                    """,
                    line.purchaseOrderItemId(),
                    line.lineId(),
                    command.receiptId()
                );

            if (lineLinked != 1) {
                throw new ProcurementConflictException(
                    "Stock receipt line could not be linked to purchase order item."
                );
            }
        }

        List<PurchaseOrderItemResponse> after =
            loadItems(
                purchaseOrderId
            );

        boolean complete =
            true;

        for (
            PurchaseOrderItemResponse item
            : after
        ) {

            if (
                item.receivedQuantity()
                    .compareTo(
                        item.quantity()
                    ) < 0
            ) {
                complete =
                    false;

                break;
            }
        }

        PurchaseOrderStatus nextStatus =
            complete
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;

        int statusUpdated =
            jdbcTemplate.update(
                """
                UPDATE purchase_orders
                SET
                    status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = ?
                """,
                nextStatus.name(),
                purchaseOrderId,
                header.status().name()
            );

        if (statusUpdated != 1) {
            throw new ProcurementConflictException(
                "Purchase order changed concurrently while receiving stock."
            );
        }

        return response(
            requireOwnedHeader(
                organizationId,
                purchaseOrderId,
                false
            )
        );
    }

    private void verifyReceiptReplay(
        PurchaseOrderHeader header,
        PurchaseOrderReceiptCommand command,
        ExistingReceipt receipt
    ) {

        if (
            receipt.purchaseOrderId() == null
            || !receipt.purchaseOrderId()
                .equals(
                    header.id()
                )
            || !sameUuid(
                receipt.supplierId(),
                header.supplierId()
            )
            || !receipt.stockLocationId()
                .equals(
                    command.stockLocationId()
                )
            || !"RECEIVED".equals(
                receipt.status()
            )
            || !Objects.equals(
                receipt.receiptReference(),
                nullableText(
                    command.receiptReference(),
                    100,
                    "Receipt reference"
                )
            )
            || !Objects.equals(
                receipt.notes(),
                nullableText(
                    command.notes()
                )
            )
        ) {
            throw new ProcurementConflictException(
                "Receipt identifier is already used by another purchase-order payload."
            );
        }

        List<ExistingReceiptLine> storedLines =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    purchase_order_item_id,
                    quantity,
                    unit_cost,
                    lot_number,
                    expires_at
                FROM stock_receipt_lines
                WHERE stock_receipt_id = ?
                ORDER BY id
                """,
                (resultSet, rowNumber) ->
                    new ExistingReceiptLine(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "purchase_order_item_id",
                            UUID.class
                        ),
                        resultSet.getBigDecimal(
                            "quantity"
                        ),
                        resultSet.getBigDecimal(
                            "unit_cost"
                        ),
                        resultSet.getString(
                            "lot_number"
                        ),
                        resultSet.getObject(
                            "expires_at",
                            OffsetDateTime.class
                        )
                    ),
                command.receiptId()
            );

        if (
            storedLines.size()
                != command.lines().size()
        ) {
            throw new ProcurementConflictException(
                "Receipt replay payload differs from stored receipt."
            );
        }

        Map<UUID, ExistingReceiptLine> byId =
            new HashMap<>();

        for (
            ExistingReceiptLine stored
            : storedLines
        ) {
            byId.put(
                stored.id(),
                stored
            );
        }

        for (
            PurchaseOrderReceiptLineCommand requested
            : command.lines()
        ) {

            ExistingReceiptLine stored =
                byId.get(
                    requested.lineId()
                );

            if (
                stored == null
                || !sameUuid(
                    stored.purchaseOrderItemId(),
                    requested.purchaseOrderItemId()
                )
                || !sameDecimal(
                    stored.quantity(),
                    requested.quantity()
                )
                || !sameDecimal(
                    stored.unitCost(),
                    requested.unitCost()
                )
                || !Objects.equals(
                    stored.lotNumber(),
                    nullableText(
                        requested.lotNumber()
                    )
                )
                || !sameInstant(
                    stored.expiresAt(),
                    requested.expiresAt()
                )
            ) {
                throw new ProcurementConflictException(
                    "Receipt replay payload differs from stored receipt."
                );
            }
        }
    }

    private ExistingReceipt existingReceipt(
        UUID organizationId,
        UUID receiptId
    ) {

        List<ExistingReceipt> rows =
            jdbcTemplate.query(
                """
                SELECT
                    sr.id,
                    sr.stock_location_id,
                    sr.supplier_id,
                    sr.purchase_order_id,
                    sr.receipt_reference,
                    sr.status,
                    sr.notes
                FROM stock_receipts sr
                JOIN stock_locations sl
                  ON sl.id = sr.stock_location_id
                JOIN locations l
                  ON l.id = sl.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE sr.id = ?
                  AND c.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    new ExistingReceipt(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "stock_location_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "supplier_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "purchase_order_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "receipt_reference"
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getString(
                            "notes"
                        )
                    ),
                receiptId,
                organizationId
            );

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private void updateStatus(
        PurchaseOrderHeader header,
        PurchaseOrderStatus next
    ) {

        int updated =
            jdbcTemplate.update(
                """
                UPDATE purchase_orders
                SET
                    status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = ?
                """,
                next.name(),
                header.id(),
                header.status().name()
            );

        if (updated != 1) {
            throw new ProcurementConflictException(
                "Purchase order changed concurrently."
            );
        }
    }

    private PurchaseOrderHeader requireOwnedHeader(
        UUID organizationId,
        UUID purchaseOrderId,
        boolean lock
    ) {

        requireId(
            purchaseOrderId,
            "Purchase order id"
        );

        PurchaseOrderHeader header =
            findOwnedHeader(
                organizationId,
                purchaseOrderId,
                lock
            );

        if (header == null) {
            throw new ProcurementNotFoundException(
                "Purchase order does not exist."
            );
        }

        return header;
    }

    private PurchaseOrderHeader findOwnedHeader(
        UUID organizationId,
        UUID purchaseOrderId,
        boolean lock
    ) {

        String sql =
            """
            SELECT
                po.id,
                po.supplier_id,
                po.campus_id,
                po.reference,
                po.status,
                po.total_estimated,
                po.created_by,
                po.approved_by,
                po.created_at,
                po.updated_at
            FROM purchase_orders po
            JOIN suppliers s
              ON s.id = po.supplier_id
            JOIN campuses c
              ON c.id = po.campus_id
            WHERE po.id = ?
              AND s.organization_id = ?
              AND c.organization_id = ?
            """
                + (
                    lock
                        ? " FOR UPDATE"
                        : ""
                );

        List<PurchaseOrderHeader> rows =
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                    header(
                        resultSet
                    ),
                purchaseOrderId,
                organizationId,
                organizationId
            );

        if (rows.size() > 1) {
            throw new ProcurementConflictException(
                "Multiple purchase orders matched one identifier."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private PurchaseOrderHeader header(
        java.sql.ResultSet resultSet
    ) throws java.sql.SQLException {

        return new PurchaseOrderHeader(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "supplier_id",
                UUID.class
            ),
            resultSet.getObject(
                "campus_id",
                UUID.class
            ),
            resultSet.getString(
                "reference"
            ),
            PurchaseOrderStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            resultSet.getBigDecimal(
                "total_estimated"
            ),
            resultSet.getObject(
                "created_by",
                UUID.class
            ),
            resultSet.getObject(
                "approved_by",
                UUID.class
            ),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "updated_at",
                OffsetDateTime.class
            )
        );
    }

    private PurchaseOrderResponse response(
        PurchaseOrderHeader header
    ) {

        return new PurchaseOrderResponse(
            header.id(),
            header.supplierId(),
            header.campusId(),
            header.reference(),
            header.status(),
            header.totalEstimated(),
            header.createdBy(),
            header.approvedBy(),
            header.createdAt(),
            header.updatedAt(),
            loadItems(
                header.id()
            )
        );
    }

    private List<PurchaseOrderItemResponse> loadItems(
        UUID purchaseOrderId
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                poi.id,
                poi.stock_item_id,
                poi.quantity,
                poi.unit,
                poi.unit_price,
                COALESCE(
                    (
                        SELECT SUM(srl.quantity)
                        FROM stock_receipt_lines srl
                        JOIN stock_receipts sr
                          ON sr.id = srl.stock_receipt_id
                        WHERE srl.purchase_order_item_id = poi.id
                          AND sr.purchase_order_id = poi.purchase_order_id
                          AND sr.status = 'RECEIVED'
                    ),
                    0
                ) AS received_quantity
            FROM purchase_order_items poi
            WHERE poi.purchase_order_id = ?
            ORDER BY poi.id
            """,
            (resultSet, rowNumber) ->
                new PurchaseOrderItemResponse(
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "stock_item_id",
                        UUID.class
                    ),
                    resultSet.getBigDecimal(
                        "quantity"
                    ),
                    MeasurementUnit.valueOf(
                        resultSet.getString(
                            "unit"
                        )
                    ),
                    resultSet.getBigDecimal(
                        "unit_price"
                    ),
                    resultSet.getBigDecimal(
                        "received_quantity"
                    )
                ),
            purchaseOrderId
        );
    }

    private void validateCreateCommand(
        PurchaseOrderCommand command
    ) {

        if (command == null) {
            throw new ProcurementValidationException(
                "Purchase order payload is required."
            );
        }

        requireId(
            command.supplierId(),
            "Supplier id"
        );

        requireId(
            command.campusId(),
            "Campus id"
        );

        requiredText(
            command.reference(),
            "Purchase order reference",
            80
        );

        nonNegativeNullable(
            command.totalEstimated(),
            "Purchase order total estimated"
        );

        if (
            command.items() == null
            || command.items().isEmpty()
        ) {
            throw new ProcurementValidationException(
                "Purchase order requires at least one item."
            );
        }

        for (
            PurchaseOrderItemCommand item
            : command.items()
        ) {

            if (item == null) {
                throw new ProcurementValidationException(
                    "Purchase order item cannot be null."
                );
            }

            requireId(
                item.stockItemId(),
                "Stock item id"
            );

            positive(
                item.quantity(),
                "Purchase order item quantity"
            );

            if (item.unit() == null) {
                throw new ProcurementValidationException(
                    "Purchase order item unit is required."
                );
            }

            nonNegativeNullable(
                item.unitPrice(),
                "Purchase order item unit price"
            );
        }
    }

    private void validateReceiptCommand(
        PurchaseOrderReceiptCommand command
    ) {

        if (command == null) {
            throw new ProcurementValidationException(
                "Purchase order receipt payload is required."
            );
        }

        requireId(
            command.receiptId(),
            "Receipt id"
        );

        requireId(
            command.stockLocationId(),
            "Stock location id"
        );

        nullableText(
            command.receiptReference(),
            100,
            "Receipt reference"
        );

        if (
            command.lines() == null
            || command.lines().isEmpty()
        ) {
            throw new ProcurementValidationException(
                "Purchase order receipt requires at least one line."
            );
        }

        for (
            PurchaseOrderReceiptLineCommand line
            : command.lines()
        ) {

            if (line == null) {
                throw new ProcurementValidationException(
                    "Purchase order receipt line cannot be null."
                );
            }
        }
    }

    private void validateSupplier(
        UUID organizationId,
        UUID supplierId
    ) {

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM suppliers
                WHERE id = ?
                  AND organization_id = ?
                  AND is_active = TRUE
                """,
                Integer.class,
                supplierId,
                organizationId
            );

        if (
            count == null
            || count != 1
        ) {
            throw new ProcurementNotFoundException(
                "Active supplier does not exist."
            );
        }
    }

    private void validateCampus(
        UUID organizationId,
        UUID campusId
    ) {

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM campuses
                WHERE id = ?
                  AND organization_id = ?
                  AND is_active = TRUE
                """,
                Integer.class,
                campusId,
                organizationId
            );

        if (
            count == null
            || count != 1
        ) {
            throw new ProcurementNotFoundException(
                "Active campus does not exist."
            );
        }
    }

    private void validateItem(
        UUID organizationId,
        PurchaseOrderItemCommand item
    ) {

        List<MeasurementUnit> units =
            jdbcTemplate.query(
                """
                SELECT base_unit
                FROM stock_items
                WHERE id = ?
                  AND organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    MeasurementUnit.valueOf(
                        resultSet.getString(
                            "base_unit"
                        )
                    ),
                item.stockItemId(),
                organizationId
            );

        if (units.size() != 1) {
            throw new ProcurementNotFoundException(
                "Stock item does not exist."
            );
        }

        if (
            units.get(0)
                != item.unit()
        ) {
            throw new ProcurementValidationException(
                "Purchase order item unit must match stock item base unit because no procurement unit-conversion contract exists."
            );
        }
    }

    private void validateStockLocationCampus(
        UUID organizationId,
        UUID campusId,
        UUID stockLocationId
    ) {

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_locations sl
                JOIN locations l
                  ON l.id = sl.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE sl.id = ?
                  AND sl.is_active = TRUE
                  AND l.is_active = TRUE
                  AND c.id = ?
                  AND c.organization_id = ?
                """,
                Integer.class,
                stockLocationId,
                campusId,
                organizationId
            );

        if (
            count == null
            || count != 1
        ) {
            throw new ProcurementNotFoundException(
                "Active stock location does not belong to purchase order campus."
            );
        }
    }

    private boolean sameCreatePayload(
        PurchaseOrderResponse stored,
        PurchaseOrderCommand requested
    ) {

        if (
            !stored.supplierId()
                .equals(
                    requested.supplierId()
                )
            || !stored.campusId()
                .equals(
                    requested.campusId()
                )
            || !Objects.equals(
                stored.reference(),
                requiredText(
                    requested.reference(),
                    "Purchase order reference",
                    80
                )
            )
            || !sameDecimal(
                stored.totalEstimated(),
                requested.totalEstimated()
            )
            || stored.items().size()
                != requested.items().size()
        ) {
            return false;
        }

        List<String> storedSignatures =
            new ArrayList<>();

        for (
            PurchaseOrderItemResponse item
            : stored.items()
        ) {
            storedSignatures.add(
                signature(
                    item.stockItemId(),
                    item.quantity(),
                    item.unit(),
                    item.unitPrice()
                )
            );
        }

        List<String> requestedSignatures =
            new ArrayList<>();

        for (
            PurchaseOrderItemCommand item
            : requested.items()
        ) {
            requestedSignatures.add(
                signature(
                    item.stockItemId(),
                    item.quantity(),
                    item.unit(),
                    item.unitPrice()
                )
            );
        }

        storedSignatures.sort(
            String::compareTo
        );

        requestedSignatures.sort(
            String::compareTo
        );

        return storedSignatures.equals(
            requestedSignatures
        );
    }

    private String signature(
        UUID stockItemId,
        BigDecimal quantity,
        MeasurementUnit unit,
        BigDecimal unitPrice
    ) {

        return stockItemId
            + "|"
            + decimalText(
                quantity
            )
            + "|"
            + unit.name()
            + "|"
            + decimalText(
                unitPrice
            );
    }

    private String decimalText(
        BigDecimal value
    ) {

        return value == null
            ? "NULL"
            : value.stripTrailingZeros()
                .toPlainString();
    }

    private UUID organizationId(
        UUID actorId
    ) {

        requireId(
            actorId,
            "Actor id"
        );

        List<UUID> organizations =
            jdbcTemplate.query(
                """
                SELECT organization_id
                FROM users
                WHERE id = ?
                """,
                (resultSet, rowNumber) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (organizations.size() != 1) {
            throw new BadCredentialsException(
                "Authenticated user does not exist."
            );
        }

        return organizations.get(0);
    }

    private void requireId(
        UUID value,
        String label
    ) {

        if (value == null) {
            throw new ProcurementValidationException(
                label + " is required."
            );
        }
    }

    private BigDecimal positive(
        BigDecimal value,
        String label
    ) {

        if (
            value == null
            || value.signum() <= 0
        ) {
            throw new ProcurementValidationException(
                label + " must be greater than zero."
            );
        }

        return value;
    }

    private BigDecimal nonNegativeNullable(
        BigDecimal value,
        String label
    ) {

        if (
            value != null
            && value.signum() < 0
        ) {
            throw new ProcurementValidationException(
                label + " cannot be negative."
            );
        }

        return value;
    }

    private String requiredText(
        String value,
        String label,
        int maxLength
    ) {

        String normalized =
            nullableText(
                value
            );

        if (normalized == null) {
            throw new ProcurementValidationException(
                label + " is required."
            );
        }

        if (normalized.length() > maxLength) {
            throw new ProcurementValidationException(
                label + " is too long."
            );
        }

        return normalized;
    }

    private String nullableText(
        String value,
        int maxLength,
        String label
    ) {

        String normalized =
            nullableText(
                value
            );

        if (
            normalized != null
            && normalized.length() > maxLength
        ) {
            throw new ProcurementValidationException(
                label + " is too long."
            );
        }

        return normalized;
    }

    private String nullableText(
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
        BigDecimal first,
        BigDecimal second
    ) {

        if (
            first == null
            || second == null
        ) {
            return first == null
                && second == null;
        }

        return first.compareTo(
            second
        ) == 0;
    }

    private boolean sameUuid(
        UUID first,
        UUID second
    ) {

        return Objects.equals(
            first,
            second
        );
    }

    private boolean sameInstant(
        OffsetDateTime first,
        OffsetDateTime second
    ) {

        if (
            first == null
            || second == null
        ) {
            return first == null
                && second == null;
        }

        return first.toInstant()
            .equals(
                second.toInstant()
            );
    }

    private record PurchaseOrderHeader(
        UUID id,
        UUID supplierId,
        UUID campusId,
        String reference,
        PurchaseOrderStatus status,
        BigDecimal totalEstimated,
        UUID createdBy,
        UUID approvedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    private record ExistingReceipt(
        UUID id,
        UUID stockLocationId,
        UUID supplierId,
        UUID purchaseOrderId,
        String receiptReference,
        String status,
        String notes
    ) {
    }

    private record ExistingReceiptLine(
        UUID id,
        UUID purchaseOrderItemId,
        BigDecimal quantity,
        BigDecimal unitCost,
        String lotNumber,
        OffsetDateTime expiresAt
    ) {
    }
}