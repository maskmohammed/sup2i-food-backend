package com.sup2i.food.scan.service;

import com.sup2i.food.order.api.dto.OrderItemResponse;
import com.sup2i.food.order.api.dto.OrderResponse;
import com.sup2i.food.order.api.dto.StockReservationResponse;
import com.sup2i.food.order.domain.OrderPaymentStatus;
import com.sup2i.food.order.domain.OrderSource;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.OrderType;
import com.sup2i.food.order.domain.StockReservationStatus;
import com.sup2i.food.scan.api.dto.OrderScanResult;
import com.sup2i.food.scan.api.dto.ProductScanProductResponse;
import com.sup2i.food.scan.api.dto.ProductScanResult;
import com.sup2i.food.scan.api.dto.ScanRequest;
import com.sup2i.food.scan.api.dto.ScanResponse;
import com.sup2i.food.scan.exception.ScanErrorCode;
import com.sup2i.food.scan.exception.ScanException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScanService {

    private static final String CURRENCY =
        "MAD";

    private final JdbcTemplate jdbcTemplate;
    private final ScanTokenHasher tokenHasher;

    public ScanService(
        JdbcTemplate jdbcTemplate,
        ScanTokenHasher tokenHasher
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.tokenHasher =
            tokenHasher;
    }

    @Transactional(
        noRollbackFor = ScanException.class
    )
    public ScanResponse resolve(
        UUID actorId,
        ScanRequest request
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        String rawValue =
            request
                .rawValue()
                .trim();

        String fingerprint =
            tokenHasher.hash(
                rawValue
            );

        UUID terminalLocationId =
            null;

        if (
            request.terminalId() != null
        ) {

            Optional<UUID> location =
                terminalLocation(
                    request.terminalId(),
                    organizationId
                );

            if (location.isEmpty()) {

                audit(
                    null,
                    actorId,
                    "UNKNOWN",
                    "ERROR",
                    null,
                    fingerprint,
                    ScanErrorCode
                        .RESOURCE_NOT_FOUND
                        .name()
                );

                throw new ScanException(
                    ScanErrorCode
                        .RESOURCE_NOT_FOUND,
                    "Terminal does not exist."
                );
            }

            terminalLocationId =
                location.get();
        }

        Optional<ProductCandidate> product =
            product(
                rawValue,
                organizationId
            );

        if (product.isPresent()) {

            ProductScanProductResponse response =
                productResponse(
                    product.get(),
                    organizationId,
                    terminalLocationId
                );

            audit(
                request.terminalId(),
                actorId,
                "PRODUCT_BARCODE",
                "SUCCESS",
                response.id(),
                fingerprint,
                null
            );

            return new ProductScanResult(
                response
            );
        }

        Optional<OrderCredential> credential =
            orderCredential(
                fingerprint
            );

        if (credential.isPresent()) {

            OrderCredential value =
                credential.get();

            Optional<OrderResponse> order =
                order(
                    value.subjectId(),
                    organizationId
                );

            if (order.isEmpty()) {

                audit(
                    request.terminalId(),
                    actorId,
                    "UNKNOWN",
                    "UNKNOWN",
                    null,
                    fingerprint,
                    ScanErrorCode
                        .RESOURCE_NOT_FOUND
                        .name()
                );

                throw new ScanException(
                    ScanErrorCode
                        .RESOURCE_NOT_FOUND,
                    "Scan does not resolve to a resource."
                );
            }

            OrderResponse response =
                order.get();

            validateOrderCredential(
                value,
                response,
                request.terminalId(),
                actorId,
                fingerprint
            );

            audit(
                request.terminalId(),
                actorId,
                "ORDER",
                "SUCCESS",
                response.id(),
                fingerprint,
                null
            );

            return new OrderScanResult(
                response
            );
        }

        audit(
            request.terminalId(),
            actorId,
            "UNKNOWN",
            "UNKNOWN",
            null,
            fingerprint,
            ScanErrorCode
                .RESOURCE_NOT_FOUND
                .name()
        );

        throw new ScanException(
            ScanErrorCode
                .RESOURCE_NOT_FOUND,
            "Scan does not resolve to a resource."
        );
    }

    private UUID organizationId(
        UUID actorId
    ) {

        List<UUID> organizations =
            jdbcTemplate.query(
                """
                SELECT u.organization_id
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (organizations.isEmpty()) {

            throw new BadCredentialsException(
                "Authenticated user does not exist."
            );
        }

        return organizations.getFirst();
    }

    private Optional<UUID> terminalLocation(
        UUID terminalId,
        UUID organizationId
    ) {

        List<UUID> locations =
            jdbcTemplate.query(
                """
                SELECT l.id
                FROM pos_terminals pt
                JOIN locations l
                  ON l.id = pt.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                JOIN organizations o
                  ON o.id = c.organization_id
                WHERE pt.id = ?
                  AND c.organization_id = ?
                  AND pt.is_active = TRUE
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                  AND o.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                terminalId,
                organizationId
            );

        return locations
            .stream()
            .findFirst();
    }

    private Optional<ProductCandidate> product(
        String barcode,
        UUID organizationId
    ) {

        List<ProductCandidate> products =
            jdbcTemplate.query(
                """
                SELECT
                    p.id,
                    p.category_id,
                    p.sku,
                    p.name,
                    p.image_url,
                    p.base_price,
                    p.track_stock,
                    p.is_active AS product_active,
                    c.is_active AS category_active,
                    pb.barcode,
                    pb.variant_id,
                    pb.pack_quantity,
                    pv.name AS variant_name,
                    pv.is_active AS variant_active
                FROM product_barcodes pb
                JOIN products p
                  ON p.id = pb.product_id
                JOIN categories c
                  ON c.id = p.category_id
                LEFT JOIN product_variants pv
                  ON pv.id = pb.variant_id
                WHERE pb.barcode = ?
                  AND pb.is_active = TRUE
                  AND p.organization_id = ?
                  AND c.organization_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new ProductCandidate(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "category_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "sku"
                        ),
                        resultSet.getString(
                            "name"
                        ),
                        resultSet.getString(
                            "image_url"
                        ),
                        resultSet.getBigDecimal(
                            "base_price"
                        ),
                        resultSet.getBoolean(
                            "track_stock"
                        ),
                        resultSet.getBoolean(
                            "product_active"
                        ),
                        resultSet.getBoolean(
                            "category_active"
                        ),
                        resultSet.getString(
                            "barcode"
                        ),
                        resultSet.getObject(
                            "variant_id",
                            UUID.class
                        ),
                        resultSet.getBigDecimal(
                            "pack_quantity"
                        ),
                        resultSet.getString(
                            "variant_name"
                        ),
                        resultSet.getObject(
                            "variant_active",
                            Boolean.class
                        )
                    ),
                barcode,
                organizationId,
                organizationId
            );

        return products
            .stream()
            .findFirst();
    }

    private ProductScanProductResponse productResponse(
        ProductCandidate candidate,
        UUID organizationId,
        UUID terminalLocationId
    ) {

        boolean active =
            candidate.productActive()
                && candidate.categoryActive()
                && (
                    candidate.variantId() == null
                    || Boolean.TRUE.equals(
                        candidate.variantActive()
                    )
                );

        boolean available =
            active;

        if (
            available
            && candidate.trackStock()
        ) {

            UUID stockItemId =
                stockItemId(
                    organizationId,
                    candidate.id(),
                    candidate.variantId()
                );

            if (stockItemId == null) {

                available =
                    false;

            } else {

                BigDecimal availableQuantity =
                    availableQuantity(
                        stockItemId,
                        organizationId,
                        terminalLocationId
                    );

                available =
                    availableQuantity.compareTo(
                        candidate.packQuantity()
                    ) >= 0;
            }
        }

        return new ProductScanProductResponse(
            candidate.id(),
            candidate.categoryId(),
            candidate.sku(),
            candidate.barcode(),
            candidate.name(),
            candidate.imageUrl(),
            candidate.basePrice(),
            CURRENCY,
            available,
            active,
            candidate.variantId(),
            candidate.variantName(),
            candidate.packQuantity()
        );
    }

    private UUID stockItemId(
        UUID organizationId,
        UUID productId,
        UUID variantId
    ) {

        if (variantId != null) {

            List<UUID> variantItems =
                jdbcTemplate.query(
                    """
                    SELECT id
                    FROM stock_items
                    WHERE organization_id = ?
                      AND variant_id = ?
                    """,
                    (
                        resultSet,
                        rowNumber
                    ) ->
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                    organizationId,
                    variantId
                );

            if (!variantItems.isEmpty()) {
                return variantItems.getFirst();
            }
        }

        List<UUID> productItems =
            jdbcTemplate.query(
                """
                SELECT id
                FROM stock_items
                WHERE organization_id = ?
                  AND product_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                organizationId,
                productId
            );

        if (productItems.isEmpty()) {
            return null;
        }

        return productItems.getFirst();
    }

    private BigDecimal availableQuantity(
        UUID stockItemId,
        UUID organizationId,
        UUID terminalLocationId
    ) {

        if (terminalLocationId != null) {

            BigDecimal quantity =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COALESCE(
                        SUM(
                            sb.physical_quantity
                            - sb.reserved_quantity
                        ),
                        0
                    )
                    FROM stock_balances sb
                    JOIN stock_locations sl
                      ON sl.id = sb.stock_location_id
                    JOIN locations l
                      ON l.id = sl.location_id
                    JOIN campuses c
                      ON c.id = l.campus_id
                    WHERE sb.stock_item_id = ?
                      AND c.organization_id = ?
                      AND l.id = ?
                      AND sl.is_active = TRUE
                      AND l.is_active = TRUE
                      AND c.is_active = TRUE
                    """,
                    BigDecimal.class,
                    stockItemId,
                    organizationId,
                    terminalLocationId
                );

            return quantity == null
                ? BigDecimal.ZERO
                : quantity;
        }

        BigDecimal quantity =
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    SUM(
                        sb.physical_quantity
                        - sb.reserved_quantity
                    ),
                    0
                )
                FROM stock_balances sb
                JOIN stock_locations sl
                  ON sl.id = sb.stock_location_id
                JOIN locations l
                  ON l.id = sl.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE sb.stock_item_id = ?
                  AND c.organization_id = ?
                  AND sl.is_active = TRUE
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                """,
                BigDecimal.class,
                stockItemId,
                organizationId
            );

        return quantity == null
            ? BigDecimal.ZERO
            : quantity;
    }

    private Optional<OrderCredential> orderCredential(
        String fingerprint
    ) {

        List<OrderCredential> credentials =
            jdbcTemplate.query(
                """
                SELECT
                    subject_id,
                    status,
                    expires_at
                FROM qr_credentials
                WHERE token_hash = ?
                  AND credential_type = 'ORDER'
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new OrderCredential(
                        resultSet.getObject(
                            "subject_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "expires_at",
                            OffsetDateTime.class
                        )
                    ),
                fingerprint
            );

        return credentials
            .stream()
            .findFirst();
    }

    private void validateOrderCredential(
        OrderCredential credential,
        OrderResponse order,
        UUID terminalId,
        UUID actorId,
        String fingerprint
    ) {

        String status =
            credential.status();

        if ("REVOKED".equals(status)) {

            rejectOrder(
                order.id(),
                terminalId,
                actorId,
                fingerprint,
                ScanErrorCode.QR_REVOKED,
                "QR credential has been revoked."
            );
        }

        boolean expiredByStatus =
            "EXPIRED".equals(status);

        boolean expiredByTime =
            credential.expiresAt() != null
                && !OffsetDateTime
                    .now()
                    .isBefore(
                        credential.expiresAt()
                    );

        if (
            expiredByStatus
            || expiredByTime
        ) {

            rejectOrder(
                order.id(),
                terminalId,
                actorId,
                fingerprint,
                ScanErrorCode.QR_EXPIRED,
                "QR credential has expired."
            );
        }

        if ("USED".equals(status)) {

            rejectOrder(
                order.id(),
                terminalId,
                actorId,
                fingerprint,
                ScanErrorCode.INVALID_QR,
                "QR credential is no longer valid."
            );
        }

        if (!"ACTIVE".equals(status)) {

            rejectOrder(
                order.id(),
                terminalId,
                actorId,
                fingerprint,
                ScanErrorCode.INVALID_QR,
                "QR credential is not valid."
            );
        }
    }

    private void rejectOrder(
        UUID orderId,
        UUID terminalId,
        UUID actorId,
        String fingerprint,
        ScanErrorCode errorCode,
        String message
    ) {

        audit(
            terminalId,
            actorId,
            "ORDER",
            "REFUSED",
            orderId,
            fingerprint,
            errorCode.name()
        );

        throw new ScanException(
            errorCode,
            message
        );
    }

    private Optional<OrderResponse> order(
        UUID orderId,
        UUID organizationId
    ) {

        List<OrderHeader> headers =
            jdbcTemplate.query(
                """
                SELECT
                    o.id,
                    o.order_number,
                    o.location_id,
                    o.student_id,
                    o.source,
                    o.status,
                    o.order_type,
                    o.payment_status,
                    o.subtotal,
                    o.tax_total,
                    o.discount_total,
                    o.total,
                    o.currency,
                    o.payment_expires_at,
                    o.customer_note,
                    o.version,
                    o.created_at,
                    o.updated_at
                FROM orders o
                WHERE o.id = ?
                  AND o.organization_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new OrderHeader(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "order_number"
                        ),
                        resultSet.getObject(
                            "location_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "student_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "source"
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getString(
                            "order_type"
                        ),
                        resultSet.getString(
                            "payment_status"
                        ),
                        resultSet.getBigDecimal(
                            "subtotal"
                        ),
                        resultSet.getBigDecimal(
                            "tax_total"
                        ),
                        resultSet.getBigDecimal(
                            "discount_total"
                        ),
                        resultSet.getBigDecimal(
                            "total"
                        ),
                        resultSet.getString(
                            "currency"
                        ),
                        resultSet.getObject(
                            "payment_expires_at",
                            OffsetDateTime.class
                        ),
                        resultSet.getString(
                            "customer_note"
                        ),
                        resultSet.getInt(
                            "version"
                        ),
                        resultSet.getObject(
                            "created_at",
                            OffsetDateTime.class
                        ),
                        resultSet.getObject(
                            "updated_at",
                            OffsetDateTime.class
                        )
                    ),
                orderId,
                organizationId
            );

        if (headers.isEmpty()) {
            return Optional.empty();
        }

        OrderHeader header =
            headers.getFirst();

        List<OrderItemResponse> items =
            orderItems(
                orderId
            );

        List<StockReservationResponse> reservations =
            reservations(
                orderId
            );

        return Optional.of(
            new OrderResponse(
                header.id(),
                header.orderNumber(),
                header.locationId(),
                header.studentId(),
                OrderSource.valueOf(
                    header.source()
                ),
                OrderStatus.valueOf(
                    header.status()
                ),
                OrderType.valueOf(
                    header.orderType()
                ),
                OrderPaymentStatus.valueOf(
                    header.paymentStatus()
                ),
                header.subtotal(),
                header.taxTotal(),
                header.discountTotal(),
                header.total(),
                header.currency(),
                header.paymentExpiresAt(),
                header.customerNote(),
                header.version(),
                header.createdAt(),
                header.updatedAt(),
                items,
                reservations
            )
        );
    }

    private List<OrderItemResponse> orderItems(
        UUID orderId
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                oi.id,
                oi.product_id,
                oi.variant_id,
                oi.product_name_snapshot,
                oi.variant_name_snapshot,
                oi.sku_snapshot,
                oi.unit_price,
                oi.quantity,
                oi.discount_amount,
                oi.line_total,
                oi.tax_rate_snapshot,
                oi.line_tax,
                oi.special_instructions
            FROM order_items oi
            WHERE oi.order_id = ?
            ORDER BY oi.id ASC
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new OrderItemResponse(
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "product_id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "variant_id",
                        UUID.class
                    ),
                    resultSet.getString(
                        "product_name_snapshot"
                    ),
                    resultSet.getString(
                        "variant_name_snapshot"
                    ),
                    resultSet.getString(
                        "sku_snapshot"
                    ),
                    resultSet.getBigDecimal(
                        "unit_price"
                    ),
                    resultSet.getInt(
                        "quantity"
                    ),
                    resultSet.getBigDecimal(
                        "discount_amount"
                    ),
                    resultSet.getBigDecimal(
                        "line_total"
                    ),
                    resultSet.getBigDecimal(
                        "tax_rate_snapshot"
                    ),
                    resultSet.getBigDecimal(
                        "line_tax"
                    ),
                    resultSet.getString(
                        "special_instructions"
                    )
                ),
            orderId
        );
    }

    private List<StockReservationResponse> reservations(
        UUID orderId
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                id,
                order_item_id,
                stock_item_id,
                stock_location_id,
                quantity,
                status,
                expires_at,
                created_at,
                consumed_at,
                released_at
            FROM stock_reservations
            WHERE order_id = ?
            ORDER BY created_at ASC, id ASC
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new StockReservationResponse(
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "order_item_id",
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
                    resultSet.getBigDecimal(
                        "quantity"
                    ),
                    StockReservationStatus.valueOf(
                        resultSet.getString(
                            "status"
                        )
                    ),
                    resultSet.getObject(
                        "expires_at",
                        OffsetDateTime.class
                    ),
                    resultSet.getObject(
                        "created_at",
                        OffsetDateTime.class
                    ),
                    resultSet.getObject(
                        "consumed_at",
                        OffsetDateTime.class
                    ),
                    resultSet.getObject(
                        "released_at",
                        OffsetDateTime.class
                    )
                ),
            orderId
        );
    }

    private void audit(
        UUID terminalId,
        UUID actorId,
        String scanType,
        String result,
        UUID resolvedReferenceId,
        String fingerprint,
        String errorCode
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO scan_events (
                terminal_id,
                operator_id,
                scan_type,
                result,
                resolved_reference_id,
                token_fingerprint,
                error_code
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            terminalId,
            actorId,
            scanType,
            result,
            resolvedReferenceId,
            fingerprint,
            errorCode
        );
    }

    private record ProductCandidate(
        UUID id,
        UUID categoryId,
        String sku,
        String name,
        String imageUrl,
        BigDecimal basePrice,
        boolean trackStock,
        boolean productActive,
        boolean categoryActive,
        String barcode,
        UUID variantId,
        BigDecimal packQuantity,
        String variantName,
        Boolean variantActive
    ) {
    }

    private record OrderCredential(
        UUID subjectId,
        String status,
        OffsetDateTime expiresAt
    ) {
    }

    private record OrderHeader(
        UUID id,
        String orderNumber,
        UUID locationId,
        UUID studentId,
        String source,
        String status,
        String orderType,
        String paymentStatus,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal discountTotal,
        BigDecimal total,
        String currency,
        OffsetDateTime paymentExpiresAt,
        String customerNote,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }
}