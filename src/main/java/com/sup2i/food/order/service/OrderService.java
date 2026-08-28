package com.sup2i.food.order.service;

import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.catalog.domain.Recipe;
import com.sup2i.food.catalog.domain.RecipeItem;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.catalog.repository.ProductVariantRepository;
import com.sup2i.food.catalog.repository.RecipeItemRepository;
import com.sup2i.food.catalog.repository.RecipeRepository;
import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.StudentRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.inventory.domain.InventoryMovement;
import com.sup2i.food.inventory.domain.InventoryMovementType;
import com.sup2i.food.inventory.domain.StockBalance;
import com.sup2i.food.inventory.domain.StockBalanceId;
import com.sup2i.food.inventory.domain.StockItem;
import com.sup2i.food.inventory.domain.StockLocation;
import com.sup2i.food.inventory.repository.InventoryMovementRepository;
import com.sup2i.food.inventory.repository.StockBalanceRepository;
import com.sup2i.food.inventory.repository.StockItemRepository;
import com.sup2i.food.inventory.repository.StockLocationRepository;
import com.sup2i.food.inventory.service.InventoryAlertService;
import com.sup2i.food.kitchen.service.KitchenTicketService;
import com.sup2i.food.order.api.dto.OrderItemResponse;
import com.sup2i.food.order.api.dto.OrderMutationResponse;
import com.sup2i.food.order.api.dto.OrderResponse;
import com.sup2i.food.order.api.dto.OrderStatusHistoryResponse;
import com.sup2i.food.order.api.dto.StockReservationResponse;
import com.sup2i.food.order.api.dto.UpsertOrderItemRequest;
import com.sup2i.food.order.api.dto.UpsertOrderRequest;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.order.domain.OrderSource;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.OrderStatusHistory;
import com.sup2i.food.order.domain.OrderStatusHistorySource;
import com.sup2i.food.order.domain.OrderType;
import com.sup2i.food.order.domain.StockReservation;
import com.sup2i.food.order.exception.OrderConflictException;
import com.sup2i.food.order.exception.OrderNotFoundException;
import com.sup2i.food.order.exception.OrderValidationException;
import com.sup2i.food.order.repository.OrderItemRepository;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.order.repository.OrderStatusHistoryRepository;
import com.sup2i.food.order.repository.StockReservationRepository;
import com.sup2i.food.organization.domain.Location;
import com.sup2i.food.organization.repository.LocationRepository;
import com.sup2i.food.payment.domain.PaymentMethod;
import com.sup2i.food.payment.service.PaymentService;
import com.sup2i.food.qr.domain.QrCredentialType;
import com.sup2i.food.qr.service.QrCredentialService;
import com.sup2i.food.timeslot.domain.TimeSlot;
import com.sup2i.food.timeslot.service.TimeSlotService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class OrderService {

    private static final int
        PAYMENT_TTL_MINUTES =
            15;

    private static final int
        MAX_ACTIVE_ORDERS =
            2;

    private static final String
        RESERVATION_REFERENCE =
            "STOCK_RESERVATION";

    private static final long
        STUDENT_LOCK_NAMESPACE =
            0x13579BDF2468ACE0L;

    private static final long
        ORDER_LOCK_NAMESPACE =
            0x2468ACE013579BDFL;

    private static final List<OrderStatus>
        ACTIVE_ORDER_STATUSES =
            List.of(
                OrderStatus.DRAFT,
                OrderStatus.CREATED,
                OrderStatus.AWAITING_PAYMENT,
                OrderStatus.PAID,
                OrderStatus.QUEUED,
                OrderStatus.PREPARING,
                OrderStatus.READY,
                OrderStatus.COLLECTED
            );

    private static final List<OrderStatus>
        PAID_OR_LATER_STATUSES =
            List.of(
                OrderStatus.PAID,
                OrderStatus.QUEUED,
                OrderStatus.PREPARING,
                OrderStatus.READY,
                OrderStatus.COLLECTED,
                OrderStatus.COMPLETED
            );

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final LocationRepository locationRepository;

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeItemRepository recipeItemRepository;

    private final StockItemRepository stockItemRepository;
    private final StockLocationRepository stockLocationRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryMovementRepository movementRepository;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final StockReservationRepository reservationRepository;

    private final InventoryAlertService inventoryAlertService;
    private final JdbcTemplate jdbcTemplate;
    private final PaymentService paymentService;
    private final QrCredentialService qrCredentialService;
    private final KitchenTicketService kitchenTicketService;
    private final TimeSlotService timeSlotService;

    public OrderService(
        UserRepository userRepository,
        StudentRepository studentRepository,
        LocationRepository locationRepository,
        ProductRepository productRepository,
        ProductVariantRepository variantRepository,
        RecipeRepository recipeRepository,
        RecipeItemRepository recipeItemRepository,
        StockItemRepository stockItemRepository,
        StockLocationRepository stockLocationRepository,
        StockBalanceRepository stockBalanceRepository,
        InventoryMovementRepository movementRepository,
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderStatusHistoryRepository historyRepository,
        StockReservationRepository reservationRepository,
        InventoryAlertService inventoryAlertService,
        JdbcTemplate jdbcTemplate,
        PaymentService paymentService,
        QrCredentialService qrCredentialService,
        KitchenTicketService kitchenTicketService,
        TimeSlotService timeSlotService
    ) {
        this.userRepository =
            userRepository;

        this.studentRepository =
            studentRepository;

        this.locationRepository =
            locationRepository;

        this.productRepository =
            productRepository;

        this.variantRepository =
            variantRepository;

        this.recipeRepository =
            recipeRepository;

        this.recipeItemRepository =
            recipeItemRepository;

        this.stockItemRepository =
            stockItemRepository;

        this.stockLocationRepository =
            stockLocationRepository;

        this.stockBalanceRepository =
            stockBalanceRepository;

        this.movementRepository =
            movementRepository;

        this.orderRepository =
            orderRepository;

        this.orderItemRepository =
            orderItemRepository;

        this.historyRepository =
            historyRepository;

        this.reservationRepository =
            reservationRepository;

        this.inventoryAlertService =
            inventoryAlertService;

        this.jdbcTemplate =
            jdbcTemplate;

        this.paymentService =
            paymentService;

        this.qrCredentialService =
            qrCredentialService;

        this.kitchenTicketService =
            kitchenTicketService;

        this.timeSlotService =
            timeSlotService;
    }

    @Transactional
    public OrderMutationResponse upsertDraft(
        UUID actorId,
        UUID orderId,
        UpsertOrderRequest request
    ) {

        ActorContext context =
            mobileStudent(actorId);

        lockStudent(
            context.student().getId()
        );

        lockOrder(orderId);

        Location location =
            ownedActiveLocation(
                request.locationId(),
                context.organizationId()
            );

        Optional<Order> existingOptional =
            orderRepository
                .findOwnedByIdForUpdate(
                    orderId,
                    context.organizationId()
                );

        Order order;
        boolean created;

        if (existingOptional.isPresent()) {

            order =
                existingOptional.get();

            requireOwnedMobileOrder(
                order,
                context.student()
            );

            if (
                order.getStatus()
                    != OrderStatus.DRAFT
            ) {
                throw new OrderConflictException(
                    "Only DRAFT orders can be edited."
                );
            }

            if (
                !order.getLocation()
                    .getId()
                    .equals(
                        location.getId()
                    )
            ) {
                throw new OrderConflictException(
                    "Order location cannot change after draft creation."
                );
            }

            created =
                false;

        } else {

            long activeOrders =
                orderRepository
                    .countByStudent_IdAndStatusIn(
                        context.student()
                            .getId(),
                        ACTIVE_ORDER_STATUSES
                    );

            if (
                activeOrders
                    >= MAX_ACTIVE_ORDERS
            ) {
                throw new OrderConflictException(
                    "Maximum active order limit reached."
                );
            }

            LocalDate businessDate =
                LocalDate.now();

            order =
                new Order(
                    orderId,
                    context.actor()
                        .getOrganization(),
                    location.getCampus(),
                    location,
                    context.student(),
                    nextOrderNumber(
                        context.organizationId(),
                        location.getId(),
                        businessDate
                    ),
                    businessDate,
                    OrderSource.MOBILE,
                    OrderType.MOBILE_SNACK,
                    normalizeCurrency(
                        request.currency()
                    ),
                    normalizeText(
                        request.customerNote()
                    )
                );

            created =
                true;
        }

        DraftSnapshot snapshot =
            buildDraftSnapshot(
                request,
                context.organizationId()
            );

        if (
            !created
            && sameDraft(
                order,
                snapshot
            )
        ) {
            return new OrderMutationResponse(
                response(order, null),
                true
            );
        }

        if (!created) {

            orderItemRepository
                .deleteAllByOrder_Id(
                    order.getId()
                );

            orderItemRepository.flush();
        }

        order.updateDraftSnapshot(
            snapshot.currency(),
            snapshot.customerNote(),
            snapshot.subtotal(),
            snapshot.taxTotal(),
            BigDecimal.ZERO
                .setScale(2),
            snapshot.total(),
            snapshot.slotId()
        );

        try {

            order =
                orderRepository
                    .saveAndFlush(order);

            List<OrderItem> lines =
                new ArrayList<>();

            for (
                DraftLineSpec spec
                : snapshot.lines()
            ) {

                lines.add(
                    new OrderItem(
                        UUID.randomUUID(),
                        order,
                        spec.product(),
                        spec.variant(),
                        spec.productName(),
                        spec.variantName(),
                        spec.sku(),
                        spec.unitPrice(),
                        spec.quantity(),
                        BigDecimal.ZERO
                            .setScale(2),
                        spec.lineTotal(),
                        spec.taxRate(),
                        spec.lineTax(),
                        spec.specialInstructions()
                    )
                );
            }

            orderItemRepository
                .saveAllAndFlush(lines);

            if (created) {

                historyRepository
                    .saveAndFlush(
                        new OrderStatusHistory(
                            order,
                            null,
                            OrderStatus.DRAFT,
                            context.actor(),
                            null,
                            OrderStatusHistorySource.MOBILE
                        )
                    );
            }

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new OrderConflictException(
                "Order conflicts with an existing resource."
            );
        }

        return new OrderMutationResponse(
            response(order, null),
            false
        );
    }

    @Transactional
    public OrderMutationResponse submit(
        UUID actorId,
        UUID orderId
    ) {

        ActorContext context =
            mobileStudent(actorId);

        Order order =
            lockedOwnedOrder(
                orderId,
                context
            );

        if (
            order.getStatus()
                == OrderStatus.CREATED
        ) {
            return new OrderMutationResponse(
                response(order, null),
                true
            );
        }

        if (
            order.getStatus()
                != OrderStatus.DRAFT
        ) {
            throw new OrderConflictException(
                "Only a DRAFT order can be submitted."
            );
        }

        List<OrderItem> items =
            orderItemRepository
                .findAllByOrder_IdOrderByIdAsc(
                    order.getId()
                );

        if (items.isEmpty()) {
            throw new OrderValidationException(
                "Order must contain at least one item."
            );
        }

        for (
            OrderItem item
            : items
        ) {
            validateStoredLine(item);
        }

        if (
            order.getSlotId()
                == null
        ) {
            throw new OrderValidationException(
                "Order must have a time slot selected."
            );
        }

        timeSlotService
            .validateSelectable(
                order.getSlotId(),
                order.getLocation()
                    .getId()
            );

        OrderStatus from =
            order.getStatus();

        order.markCreated();

        historyRepository.save(
            new OrderStatusHistory(
                order,
                from,
                OrderStatus.CREATED,
                context.actor(),
                null,
                OrderStatusHistorySource.MOBILE
            )
        );

        orderRepository
            .saveAndFlush(order);

        return new OrderMutationResponse(
            response(order, null),
            false
        );
    }

    @Transactional
    public OrderMutationResponse beginPayment(
        UUID actorId,
        UUID orderId
    ) {

        ActorContext context =
            mobileStudent(actorId);

        Order order =
            lockedOwnedOrder(
                orderId,
                context
            );

        if (
            order.getStatus()
                == OrderStatus.AWAITING_PAYMENT
        ) {
            return new OrderMutationResponse(
                response(order, null),
                true
            );
        }

        if (
            order.getStatus()
                != OrderStatus.CREATED
        ) {
            throw new OrderConflictException(
                "Only a CREATED order can begin payment."
            );
        }

        List<OrderItem> items =
            orderItemRepository
                .findAllByOrder_IdOrderByIdAsc(
                    order.getId()
                );

        if (items.isEmpty()) {
            throw new OrderValidationException(
                "Order must contain at least one item."
            );
        }

        for (
            OrderItem item
            : items
        ) {
            validateStoredLine(item);
        }

        OffsetDateTime expiresAt =
            OffsetDateTime.now()
                .plusMinutes(
                    PAYMENT_TTL_MINUTES
                );

        reserveStock(
            order,
            items,
            context.actor(),
            expiresAt
        );

        timeSlotService.reserve(
            order
        );

        OrderStatus from =
            order.getStatus();

        order.markAwaitingPayment(
            expiresAt
        );

        String qrToken =
            qrCredentialService.issue(
                QrCredentialType.ORDER,
                order.getId(),
                expiresAt
            );

        historyRepository.save(
            new OrderStatusHistory(
                order,
                from,
                OrderStatus.AWAITING_PAYMENT,
                context.actor(),
                null,
                OrderStatusHistorySource.MOBILE
            )
        );

        orderRepository
            .saveAndFlush(order);

        inventoryAlertService
            .reconcileOrganization(
                context.organizationId()
            );

        return new OrderMutationResponse(
            response(order, qrToken),
            false
        );
    }

    @Transactional
    public OrderMutationResponse pay(
        UUID actorId,
        UUID orderId
    ) {

        ActorContext context =
            mobileStudent(actorId);

        Order order =
            lockedOwnedOrder(
                orderId,
                context
            );

        if (
            PAID_OR_LATER_STATUSES
                .contains(
                    order.getStatus()
                )
        ) {
            return new OrderMutationResponse(
                response(order, null),
                true
            );
        }

        if (
            order.getStatus()
                != OrderStatus.AWAITING_PAYMENT
        ) {
            throw new OrderConflictException(
                "Only an AWAITING_PAYMENT order can be paid."
            );
        }

        OrderStatus from =
            order.getStatus();

        OffsetDateTime now =
            OffsetDateTime.now();

        order.markPaid(now);

        paymentService.recordCompletedPayment(
            order,
            PaymentMethod.ONLINE,
            order.getTotal(),
            now
        );

        historyRepository.save(
            new OrderStatusHistory(
                order,
                from,
                OrderStatus.PAID,
                context.actor(),
                null,
                OrderStatusHistorySource.MOBILE
            )
        );

        kitchenTicketService
            .createTicketForPaidOrder(
                order
            );

        OrderStatus paidStatus =
            order.getStatus();

        order.markQueued();

        historyRepository.save(
            new OrderStatusHistory(
                order,
                paidStatus,
                OrderStatus.QUEUED,
                context.actor(),
                "Kitchen ticket created.",
                OrderStatusHistorySource.MOBILE
            )
        );

        orderRepository
            .saveAndFlush(order);

        return new OrderMutationResponse(
            response(order, null),
            false
        );
    }

    @Transactional
    public OrderMutationResponse cancel(
        UUID actorId,
        UUID orderId
    ) {

        ActorContext context =
            mobileStudent(actorId);

        Order order =
            lockedOwnedOrder(
                orderId,
                context
            );

        if (
            order.getStatus()
                == OrderStatus.CANCELLED
        ) {
            return new OrderMutationResponse(
                response(order, null),
                true
            );
        }

        boolean allowed =
            order.getStatus()
                == OrderStatus.DRAFT
            || order.getStatus()
                == OrderStatus.CREATED
            || order.getStatus()
                == OrderStatus.AWAITING_PAYMENT;

        if (!allowed) {
            throw new OrderConflictException(
                "Order can no longer be cancelled."
            );
        }

        OrderStatus from =
            order.getStatus();

        boolean released =
            false;

        if (
            from
                == OrderStatus.AWAITING_PAYMENT
        ) {
            released =
                releaseReservations(
                    order,
                    context.actor(),
                    false
                );

            timeSlotService.release(
                order,
                false
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        order.markCancelled(now);

        historyRepository.save(
            new OrderStatusHistory(
                order,
                from,
                OrderStatus.CANCELLED,
                context.actor(),
                null,
                OrderStatusHistorySource.MOBILE
            )
        );

        orderRepository
            .saveAndFlush(order);

        if (released) {
            inventoryAlertService
                .reconcileOrganization(
                    context.organizationId()
                );
        }

        return new OrderMutationResponse(
            response(order, null),
            false
        );
    }

    @Transactional
    public OrderMutationResponse expire(
        UUID actorId,
        UUID orderId
    ) {

        ActorContext context =
            mobileStudent(actorId);

        Order order =
            lockedOwnedOrder(
                orderId,
                context
            );

        if (
            order.getStatus()
                == OrderStatus.EXPIRED
        ) {
            return new OrderMutationResponse(
                response(order, null),
                true
            );
        }

        if (
            order.getStatus()
                != OrderStatus.AWAITING_PAYMENT
        ) {
            throw new OrderConflictException(
                "Only an AWAITING_PAYMENT order can expire."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        if (
            order.getPaymentExpiresAt()
                == null
            || now.isBefore(
                order.getPaymentExpiresAt()
            )
        ) {
            throw new OrderConflictException(
                "Payment window has not expired."
            );
        }

        boolean released =
            releaseReservations(
                order,
                context.actor(),
                true
            );

        timeSlotService.release(
            order,
            true
        );

        OrderStatus from =
            order.getStatus();

        order.markExpired();

        historyRepository.save(
            new OrderStatusHistory(
                order,
                from,
                OrderStatus.EXPIRED,
                context.actor(),
                "Payment window expired.",
                OrderStatusHistorySource.SYSTEM
            )
        );

        orderRepository
            .saveAndFlush(order);

        if (released) {
            inventoryAlertService
                .reconcileOrganization(
                    context.organizationId()
                );
        }

        return new OrderMutationResponse(
            response(order, null),
            false
        );
    }

    @Transactional(readOnly = true)
    public OrderResponse find(
        UUID actorId,
        UUID orderId
    ) {

        ActorContext context =
            mobileStudent(actorId);

        Order order =
            orderRepository
                .findByIdAndOrganization_IdAndStudent_Id(
                    orderId,
                    context.organizationId(),
                    context.student()
                        .getId()
                )
                .orElseThrow(() ->
                    new OrderNotFoundException(
                        "Order does not exist."
                    )
                );

        requireOwnedMobileOrder(
            order,
            context.student()
        );

        return response(order, null);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse>
        history(
            UUID actorId,
            UUID orderId
        ) {

        ActorContext context =
            mobileStudent(actorId);

        Order order =
            orderRepository
                .findByIdAndOrganization_IdAndStudent_Id(
                    orderId,
                    context.organizationId(),
                    context.student()
                        .getId()
                )
                .orElseThrow(() ->
                    new OrderNotFoundException(
                        "Order does not exist."
                    )
                );

        requireOwnedMobileOrder(
            order,
            context.student()
        );

        return historyRepository
            .findAllByOrder_IdOrderByCreatedAtAsc(
                orderId
            )
            .stream()
            .map(history ->
                new OrderStatusHistoryResponse(
                    history.getId(),
                    history.getFromStatus(),
                    history.getToStatus(),
                    history.getChangedBy()
                        == null
                            ? null
                            : history
                                .getChangedBy()
                                .getId(),
                    history.getReason(),
                    history.getSource(),
                    history.getCreatedAt()
                )
            )
            .toList();
    }

    private DraftSnapshot buildDraftSnapshot(
        UpsertOrderRequest request,
        UUID organizationId
    ) {

        String currency =
            normalizeCurrency(
                request.currency()
            );

        String customerNote =
            normalizeText(
                request.customerNote()
            );

        List<DraftLineSpec> lines =
            new ArrayList<>();

        BigDecimal subtotal =
            BigDecimal.ZERO
                .setScale(2);

        for (
            UpsertOrderItemRequest line
            : request.items()
        ) {

            Product product =
                productRepository
                    .findCatalogProduct(
                        line.productId(),
                        organizationId
                    )
                    .orElseThrow(() ->
                        new OrderValidationException(
                            "Product does not exist."
                        )
                    );

            validateProduct(product);

            ProductVariant variant =
                null;

            if (
                line.variantId()
                    != null
            ) {

                variant =
                    variantRepository
                        .findByIdAndProduct_Id(
                            line.variantId(),
                            product.getId()
                        )
                        .orElseThrow(() ->
                            new OrderValidationException(
                                "Product variant does not exist."
                            )
                        );

                if (!variant.isActive()) {
                    throw new OrderConflictException(
                        "Product variant is inactive."
                    );
                }
            }

            BigDecimal unitPrice =
                product.getBasePrice()
                    .add(
                        variant == null
                            ? BigDecimal.ZERO
                            : variant.getPriceDelta()
                    )
                    .setScale(
                        2,
                        RoundingMode.HALF_UP
                    );

            if (
                unitPrice.signum() < 0
            ) {
                throw new OrderConflictException(
                    "Calculated product price cannot be negative."
                );
            }

            BigDecimal lineTotal =
                unitPrice
                    .multiply(
                        BigDecimal.valueOf(
                            line.quantity()
                        )
                    )
                    .setScale(
                        2,
                        RoundingMode.HALF_UP
                    );

            BigDecimal taxRate =
                product.getTaxRate()
                    .setScale(
                        2,
                        RoundingMode.HALF_UP
                    );

            BigDecimal lineTax =
                BigDecimal.ZERO
                    .setScale(2);

            DraftLineSpec spec =
                new DraftLineSpec(
                    product,
                    variant,
                    product.getName(),
                    variant == null
                        ? null
                        : variant.getName(),
                    variant != null
                        && variant.getSku() != null
                            ? variant.getSku()
                            : product.getSku(),
                    unitPrice,
                    line.quantity(),
                    taxRate,
                    lineTax,
                    lineTotal,
                    normalizeText(
                        line.specialInstructions()
                    )
                );

            lines.add(spec);

            subtotal =
                subtotal.add(
                    lineTotal
                );
        }

        subtotal =
            subtotal.setScale(
                2,
                RoundingMode.HALF_UP
            );

        BigDecimal taxTotal =
            BigDecimal.ZERO
                .setScale(2);

        BigDecimal total =
            subtotal;

        UUID slotId =
            null;

        if (
            request.timeSlotId()
                != null
        ) {

            TimeSlot slot =
                timeSlotService
                    .validateSelectable(
                        request.timeSlotId(),
                        request.locationId()
                    );

            slotId =
                slot.getId();
        }

        return new DraftSnapshot(
            currency,
            customerNote,
            subtotal,
            taxTotal,
            total,
            List.copyOf(lines),
            slotId
        );
    }

    private void validateStoredLine(
        OrderItem item
    ) {

        Product product =
            item.getProduct();

        validateProduct(product);

        ProductVariant variant =
            item.getVariant();

        if (
            variant != null
            && (
                !variant.isActive()
                || !variant
                    .getProduct()
                    .getId()
                    .equals(
                        product.getId()
                    )
            )
        ) {
            throw new OrderConflictException(
                "Product variant is no longer available."
            );
        }

        BigDecimal currentPrice =
            product.getBasePrice()
                .add(
                    variant == null
                        ? BigDecimal.ZERO
                        : variant.getPriceDelta()
                )
                .setScale(
                    2,
                    RoundingMode.HALF_UP
                );

        if (
            currentPrice.compareTo(
                item.getUnitPrice()
            ) != 0
        ) {
            throw new OrderConflictException(
                "Order pricing changed. Refresh the draft before continuing."
            );
        }
    }

    private void validateProduct(
        Product product
    ) {

        if (
            !product.isActive()
            || !product
                .getCategory()
                .isActive()
        ) {
            throw new OrderConflictException(
                "Product is no longer available."
            );
        }
    }

    private void reserveStock(
        Order order,
        List<OrderItem> items,
        User actor,
        OffsetDateTime expiresAt
    ) {

        List<Requirement> requirements =
            requirementsForOrder(
                order,
                items
            );

        if (requirements.isEmpty()) {
            return;
        }

        List<StockLocation> locations =
            new ArrayList<>(
                stockLocationRepository
                    .findAllByLocation_IdAndActiveTrue(
                        order.getLocation()
                            .getId()
                    )
            );

        locations.sort(
            Comparator.comparing(
                StockLocation::getId
            )
        );

        if (locations.isEmpty()) {
            throw new OrderConflictException(
                "No active stock location is configured for this order location."
            );
        }

        List<UUID> locationIds =
            locations.stream()
                .map(
                    StockLocation::getId
                )
                .toList();

        Map<UUID, List<Requirement>>
            byStockItem =
                new TreeMap<>();

        for (
            Requirement requirement
            : requirements
        ) {
            byStockItem
                .computeIfAbsent(
                    requirement
                        .stockItem()
                        .getId(),
                    ignored ->
                        new ArrayList<>()
                )
                .add(requirement);
        }

        for (
            Map.Entry<
                UUID,
                List<Requirement>
            > entry
            : byStockItem.entrySet()
        ) {

            UUID stockItemId =
                entry.getKey();

            List<Requirement>
                itemRequirements =
                    entry.getValue();

            itemRequirements.sort(
                Comparator.comparing(
                    requirement ->
                        requirement
                            .orderItem()
                            .getId()
                )
            );

            BigDecimal required =
                itemRequirements
                    .stream()
                    .map(
                        Requirement::quantity
                    )
                    .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                    );

            List<StockBalance> balances =
                stockBalanceRepository
                    .findLockedForAllocation(
                        stockItemId,
                        locationIds
                    );

            BigDecimal available =
                balances.stream()
                    .map(
                        StockBalance::
                            getAvailableQuantity
                    )
                    .filter(
                        quantity ->
                            quantity.signum() > 0
                    )
                    .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                    );

            if (
                available.compareTo(
                    required
                ) < 0
            ) {
                throw new OrderConflictException(
                    "Insufficient available stock for one or more order items."
                );
            }

            int balanceIndex =
                0;

            for (
                Requirement requirement
                : itemRequirements
            ) {

                BigDecimal remaining =
                    requirement.quantity();

                while (
                    remaining.signum() > 0
                ) {

                    while (
                        balanceIndex
                            < balances.size()
                        && balances
                            .get(balanceIndex)
                            .getAvailableQuantity()
                            .signum() <= 0
                    ) {
                        balanceIndex++;
                    }

                    if (
                        balanceIndex
                            >= balances.size()
                    ) {
                        throw new OrderConflictException(
                            "Insufficient available stock."
                        );
                    }

                    StockBalance balance =
                        balances.get(
                            balanceIndex
                        );

                    BigDecimal take =
                        balance
                            .getAvailableQuantity()
                            .min(remaining);

                    balance.reserve(take);

                    StockReservation reservation =
                        reservationRepository
                            .save(
                                new StockReservation(
                                    order,
                                    requirement
                                        .orderItem(),
                                    requirement
                                        .stockItem(),
                                    balance
                                        .getStockLocation(),
                                    take,
                                    expiresAt
                                )
                            );

                    movementRepository.save(
                        new InventoryMovement(
                            requirement
                                .stockItem(),
                            balance
                                .getStockLocation(),
                            InventoryMovementType
                                .RESERVATION,
                            BigDecimal.ZERO,
                            take,
                            requirement
                                .stockItem()
                                .getBaseUnit(),
                            null,
                            RESERVATION_REFERENCE,
                            reservation.getId(),
                            "Order stock reservation",
                            order.getOrderNumber(),
                            actor
                        )
                    );

                    remaining =
                        remaining.subtract(
                            take
                        );
                }
            }
        }

        reservationRepository.flush();
        movementRepository.flush();
    }

    private List<Requirement>
        requirementsForOrder(
            Order order,
            List<OrderItem> items
        ) {

        Map<RequirementKey, Requirement>
            aggregated =
                new TreeMap<>(
                    Comparator
                        .comparing(
                            (RequirementKey key) ->
                                key.stockItemId()
                        )
                        .thenComparing(
                            RequirementKey::
                                orderItemId
                        )
                );

        for (
            OrderItem item
            : items
        ) {

            Product product =
                item.getProduct();

            if (product.isPrepared()) {

                addPreparedRequirements(
                    item,
                    aggregated
                );

                continue;
            }

            if (!product.isTrackStock()) {
                continue;
            }

            StockItem stockItem;

            if (
                item.getVariant()
                    != null
            ) {
                stockItem =
                    stockItemRepository
                        .findByOrganization_IdAndVariant_Id(
                            order.getOrganization()
                                .getId(),
                            item.getVariant()
                                .getId()
                        )
                        .orElseThrow(() ->
                            new OrderConflictException(
                                "Variant stock item is not configured."
                            )
                        );
            } else {
                stockItem =
                    stockItemRepository
                        .findByOrganization_IdAndProduct_Id(
                            order.getOrganization()
                                .getId(),
                            product.getId()
                        )
                        .orElseThrow(() ->
                            new OrderConflictException(
                                "Product stock item is not configured."
                            )
                        );
            }

            if (
                stockItem.getBaseUnit()
                    != MeasurementUnit.PIECE
            ) {
                throw new OrderConflictException(
                    "Packaged product stock unit must be PIECE."
                );
            }

            mergeRequirement(
                aggregated,
                item,
                stockItem,
                BigDecimal.valueOf(
                    item.getQuantity()
                ).setScale(3)
            );
        }

        return List.copyOf(
            aggregated.values()
        );
    }

    private void addPreparedRequirements(
        OrderItem item,
        Map<RequirementKey, Requirement>
            aggregated
    ) {

        Product product =
            item.getProduct();

        UUID variantId =
            item.getVariant()
                == null
                    ? null
                    : item
                        .getVariant()
                        .getId();

        OffsetDateTime now =
            OffsetDateTime.now();

        Optional<Recipe> recipeOptional;

        if (variantId != null) {

            recipeOptional =
                recipeRepository
                    .findCurrent(
                        product.getId(),
                        variantId
                    )
                    .filter(
                        recipe ->
                            effectiveNow(
                                recipe,
                                now
                            )
                    );

            if (recipeOptional.isEmpty()) {
                recipeOptional =
                    recipeRepository
                        .findCurrent(
                            product.getId(),
                            null
                        )
                        .filter(
                            recipe ->
                                effectiveNow(
                                    recipe,
                                    now
                                )
                        );
            }

        } else {

            recipeOptional =
                recipeRepository
                    .findCurrent(
                        product.getId(),
                        null
                    )
                    .filter(
                        recipe ->
                            effectiveNow(
                                recipe,
                                now
                            )
                    );
        }

        Recipe recipe =
            recipeOptional
                .orElseThrow(() ->
                    new OrderConflictException(
                        "Prepared product has no active recipe."
                    )
                );

        List<RecipeItem> recipeItems =
            recipeItemRepository
                .findAllByRecipe_IdOrderByIngredient_NameAsc(
                    recipe.getId()
                );

        if (recipeItems.isEmpty()) {
            throw new OrderConflictException(
                "Prepared product recipe is empty."
            );
        }

        for (
            RecipeItem recipeItem
            : recipeItems
        ) {

            Ingredient ingredient =
                recipeItem.getIngredient();

            if (!ingredient.isActive()) {
                throw new OrderConflictException(
                    "Recipe contains an inactive ingredient."
                );
            }

            if (!ingredient.isTrackStock()) {
                continue;
            }

            StockItem stockItem =
                stockItemRepository
                    .findByOrganization_IdAndIngredient_Id(
                        product
                            .getOrganization()
                            .getId(),
                        ingredient.getId()
                    )
                    .orElseThrow(() ->
                        new OrderConflictException(
                            "Ingredient stock item is not configured."
                        )
                    );

            if (
                recipeItem.getUnit()
                    != ingredient.getBaseUnit()
                || stockItem.getBaseUnit()
                    != recipeItem.getUnit()
            ) {
                throw new OrderConflictException(
                    "Recipe unit conversion is not configured."
                );
            }

            BigDecimal wasteFactor =
                recipeItem.getWasteFactor()
                    == null
                        ? BigDecimal.ZERO
                        : recipeItem
                            .getWasteFactor();

            BigDecimal quantity =
                recipeItem
                    .getQuantity()
                    .multiply(
                        BigDecimal.valueOf(
                            item.getQuantity()
                        )
                    )
                    .multiply(
                        BigDecimal.ONE.add(
                            wasteFactor
                        )
                    )
                    .setScale(
                        3,
                        RoundingMode.CEILING
                    );

            mergeRequirement(
                aggregated,
                item,
                stockItem,
                quantity
            );
        }
    }

    private void mergeRequirement(
        Map<RequirementKey, Requirement>
            aggregated,
        OrderItem orderItem,
        StockItem stockItem,
        BigDecimal quantity
    ) {

        RequirementKey key =
            new RequirementKey(
                stockItem.getId(),
                orderItem.getId()
            );

        Requirement previous =
            aggregated.get(key);

        if (previous == null) {

            aggregated.put(
                key,
                new Requirement(
                    orderItem,
                    stockItem,
                    quantity
                )
            );

            return;
        }

        aggregated.put(
            key,
            new Requirement(
                orderItem,
                stockItem,
                previous
                    .quantity()
                    .add(quantity)
                    .setScale(
                        3,
                        RoundingMode.CEILING
                    )
            )
        );
    }

    private boolean releaseReservations(
        Order order,
        User actor,
        boolean expired
    ) {

        List<StockReservation> reservations =
            reservationRepository
                .findActiveByOrderForUpdate(
                    order.getId()
                );

        if (reservations.isEmpty()) {
            return false;
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        for (
            StockReservation reservation
            : reservations
        ) {

            StockBalance balance =
                stockBalanceRepository
                    .findLockedById(
                        new StockBalanceId(
                            reservation
                                .getStockItem()
                                .getId(),
                            reservation
                                .getStockLocation()
                                .getId()
                        )
                    )
                    .orElseThrow(() ->
                        new OrderConflictException(
                            "Reserved stock balance does not exist."
                        )
                    );

            balance.releaseReserved(
                reservation.getQuantity()
            );

            if (expired) {
                reservation.expire(now);
            } else {
                reservation.release(now);
            }

            movementRepository.save(
                new InventoryMovement(
                    reservation
                        .getStockItem(),
                    reservation
                        .getStockLocation(),
                    InventoryMovementType
                        .RESERVATION_RELEASE,
                    BigDecimal.ZERO,
                    reservation
                        .getQuantity()
                        .negate(),
                    reservation
                        .getStockItem()
                        .getBaseUnit(),
                    null,
                    RESERVATION_REFERENCE,
                    reservation.getId(),
                    expired
                        ? "Order reservation expired"
                        : "Order reservation released",
                    order.getOrderNumber(),
                    actor
                )
            );
        }

        reservationRepository.flush();
        movementRepository.flush();

        return true;
    }

    private boolean effectiveNow(
        Recipe recipe,
        OffsetDateTime now
    ) {

        boolean started =
            recipe.getEffectiveFrom()
                == null
            || !recipe
                .getEffectiveFrom()
                .isAfter(now);

        boolean notEnded =
            recipe.getEffectiveTo()
                == null
            || recipe
                .getEffectiveTo()
                .isAfter(now);

        return recipe.isActive()
            && started
            && notEnded;
    }

    private boolean sameDraft(
        Order order,
        DraftSnapshot snapshot
    ) {

        if (
            !order.getCurrency()
                .equals(
                    snapshot.currency()
                )
        ) {
            return false;
        }

        if (
            !sameText(
                order.getCustomerNote(),
                snapshot.customerNote()
            )
        ) {
            return false;
        }

        if (
            !Objects.equals(
                order.getSlotId(),
                snapshot.slotId()
            )
        ) {
            return false;
        }

        if (
            order.getSubtotal()
                .compareTo(
                    snapshot.subtotal()
                ) != 0
            || order.getTaxTotal()
                .compareTo(
                    snapshot.taxTotal()
                ) != 0
            || order.getTotal()
                .compareTo(
                    snapshot.total()
                ) != 0
        ) {
            return false;
        }

        List<String> existing =
            orderItemRepository
                .findAllByOrder_IdOrderByIdAsc(
                    order.getId()
                )
                .stream()
                .map(
                    this::lineSignature
                )
                .sorted()
                .toList();

        List<String> desired =
            snapshot.lines()
                .stream()
                .map(
                    this::lineSignature
                )
                .sorted()
                .toList();

        return existing.equals(
            desired
        );
    }

    private String lineSignature(
        OrderItem item
    ) {

        return String.join(
            "|",
            item.getProduct()
                .getId()
                .toString(),
            item.getVariant()
                == null
                    ? ""
                    : item
                        .getVariant()
                        .getId()
                        .toString(),
            item.getProductNameSnapshot(),
            nullSafe(
                item.getVariantNameSnapshot()
            ),
            nullSafe(
                item.getSkuSnapshot()
            ),
            money(
                item.getUnitPrice()
            ),
            Integer.toString(
                item.getQuantity()
            ),
            money(
                item.getTaxRateSnapshot()
            ),
            money(
                item.getLineTax()
            ),
            money(
                item.getLineTotal()
            ),
            nullSafe(
                normalizeText(
                    item.getSpecialInstructions()
                )
            )
        );
    }

    private String lineSignature(
        DraftLineSpec line
    ) {

        return String.join(
            "|",
            line.product()
                .getId()
                .toString(),
            line.variant()
                == null
                    ? ""
                    : line
                        .variant()
                        .getId()
                        .toString(),
            line.productName(),
            nullSafe(
                line.variantName()
            ),
            nullSafe(
                line.sku()
            ),
            money(
                line.unitPrice()
            ),
            Integer.toString(
                line.quantity()
            ),
            money(
                line.taxRate()
            ),
            money(
                line.lineTax()
            ),
            money(
                line.lineTotal()
            ),
            nullSafe(
                line.specialInstructions()
            )
        );
    }

    private Order lockedOwnedOrder(
        UUID orderId,
        ActorContext context
    ) {

        lockOrder(orderId);

        Order order =
            orderRepository
                .findOwnedByIdForUpdate(
                    orderId,
                    context.organizationId()
                )
                .orElseThrow(() ->
                    new OrderNotFoundException(
                        "Order does not exist."
                    )
                );

        requireOwnedMobileOrder(
            order,
            context.student()
        );

        return order;
    }

    private void requireOwnedMobileOrder(
        Order order,
        Student student
    ) {

        boolean owned =
            order.getStudent()
                != null
            && order
                .getStudent()
                .getId()
                .equals(
                    student.getId()
                );

        boolean mobile =
            order.getSource()
                == OrderSource.MOBILE
            && order.getOrderType()
                == OrderType.MOBILE_SNACK;

        if (
            !owned
            || !mobile
        ) {
            throw new OrderNotFoundException(
                "Order does not exist."
            );
        }
    }

    private ActorContext mobileStudent(
        UUID actorId
    ) {

        User actor =
            userRepository
                .findById(actorId)
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Authenticated user does not exist."
                    )
                );

        if (
            !actor.getOrganization()
                .isActive()
        ) {
            throw new OrderConflictException(
                "Organization is inactive."
            );
        }

        Student student =
            studentRepository
                .findByUserId(actorId)
                .orElseThrow(() ->
                    new OrderConflictException(
                        "Authenticated user is not an active student."
                    )
                );

        if (
            !"ACTIVE".equals(
                student
                    .getEnrollmentStatus()
                    .name()
            )
        ) {
            throw new OrderConflictException(
                "Student enrollment is not active."
            );
        }

        return new ActorContext(
            actor,
            student,
            actor.getOrganization()
                .getId()
        );
    }

    private Location ownedActiveLocation(
        UUID locationId,
        UUID organizationId
    ) {

        Location location =
            locationRepository
                .findByIdAndCampus_Organization_Id(
                    locationId,
                    organizationId
                )
                .orElseThrow(() ->
                    new OrderValidationException(
                        "Location does not exist."
                    )
                );

        if (
            !location.isActive()
            || !location
                .getCampus()
                .isActive()
            || !location
                .getCampus()
                .getOrganization()
                .isActive()
        ) {
            throw new OrderConflictException(
                "Location is inactive."
            );
        }

        return location;
    }

    private String nextOrderNumber(
        UUID organizationId,
        UUID locationId,
        LocalDate businessDate
    ) {

        Long value =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO document_sequences (
                    organization_id,
                    location_id,
                    sequence_type,
                    business_date,
                    prefix,
                    current_value,
                    padding,
                    updated_at
                )
                VALUES (
                    ?,
                    ?,
                    'ORDER',
                    ?,
                    'ORD',
                    1,
                    4,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (
                    organization_id,
                    location_id,
                    sequence_type,
                    business_date,
                    prefix
                )
                DO UPDATE SET
                    current_value =
                        document_sequences.current_value + 1,
                    updated_at =
                        CURRENT_TIMESTAMP
                RETURNING current_value
                """,
                Long.class,
                organizationId,
                locationId,
                businessDate
            );

        if (value == null) {
            throw new OrderConflictException(
                "Unable to allocate order number."
            );
        }

        return "ORD-"
            + businessDate
                .toString()
                .replace("-", "")
            + "-"
            + String.format(
                Locale.ROOT,
                "%04d",
                value
            );
    }

    private OrderResponse response(
        Order order,
        String qrToken
    ) {

        List<OrderItemResponse> items =
            orderItemRepository
                .findAllByOrder_IdOrderByIdAsc(
                    order.getId()
                )
                .stream()
                .map(item ->
                    new OrderItemResponse(
                        item.getId(),
                        item.getProduct()
                            .getId(),
                        item.getVariant()
                            == null
                                ? null
                                : item
                                    .getVariant()
                                    .getId(),
                        item.getProductNameSnapshot(),
                        item.getVariantNameSnapshot(),
                        item.getSkuSnapshot(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getDiscountAmount(),
                        item.getLineTotal(),
                        item.getTaxRateSnapshot(),
                        item.getLineTax(),
                        item.getSpecialInstructions()
                    )
                )
                .toList();

        List<StockReservationResponse>
            reservations =
                reservationRepository
                    .findAllByOrder_IdOrderByCreatedAtAsc(
                        order.getId()
                    )
                    .stream()
                    .map(reservation ->
                        new StockReservationResponse(
                            reservation.getId(),
                            reservation.getOrderItem()
                                == null
                                    ? null
                                    : reservation
                                        .getOrderItem()
                                        .getId(),
                            reservation
                                .getStockItem()
                                .getId(),
                            reservation
                                .getStockLocation()
                                .getId(),
                            reservation.getQuantity(),
                            reservation.getStatus(),
                            reservation.getExpiresAt(),
                            reservation.getCreatedAt(),
                            reservation.getConsumedAt(),
                            reservation.getReleasedAt()
                        )
                    )
                    .toList();

        return new OrderResponse(
            order.getId(),
            order.getOrderNumber(),
            order.getLocation()
                .getId(),
            order.getStudent()
                == null
                    ? null
                    : order
                        .getStudent()
                        .getId(),
            order.getSlotId(),
            order.getSource(),
            order.getStatus(),
            order.getOrderType(),
            order.getPaymentStatus(),
            order.getSubtotal(),
            order.getTaxTotal(),
            order.getDiscountTotal(),
            order.getTotal(),
            order.getCurrency(),
            order.getPaymentExpiresAt(),
            order.getCustomerNote(),
            order.getVersion(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            items,
            reservations,
            qrToken
        );
    }

    private String normalizeCurrency(
        String currency
    ) {

        String normalized =
            currency == null
            || currency.isBlank()
                ? "MAD"
                : currency
                    .trim()
                    .toUpperCase(
                        Locale.ROOT
                    );

        if (
            !normalized.matches(
                "^[A-Z]{3}$"
            )
        ) {
            throw new OrderValidationException(
                "Currency must contain exactly three letters."
            );
        }

        return normalized;
    }

    private String normalizeText(
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

    private boolean sameText(
        String left,
        String right
    ) {
        return nullSafe(
            normalizeText(left)
        ).equals(
            nullSafe(
                normalizeText(right)
            )
        );
    }

    private String nullSafe(
        String value
    ) {
        return value == null
            ? ""
            : value;
    }

    private String money(
        BigDecimal value
    ) {
        return value
            .setScale(
                2,
                RoundingMode.HALF_UP
            )
            .toPlainString();
    }

    private void lockStudent(
        UUID studentId
    ) {
        lockUuid(
            studentId,
            STUDENT_LOCK_NAMESPACE
        );
    }

    private void lockOrder(
        UUID orderId
    ) {
        lockUuid(
            orderId,
            ORDER_LOCK_NAMESPACE
        );
    }

    private void lockUuid(
        UUID id,
        long namespace
    ) {

        long key =
            id.getMostSignificantBits()
            ^ id.getLeastSignificantBits()
            ^ namespace;

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(?)",
            statement ->
                statement.setLong(
                    1,
                    key
                ),
            (ResultSetExtractor<Void>)
                resultSet -> null
        );
    }

    private record ActorContext(
        User actor,
        Student student,
        UUID organizationId
    ) {
    }

    private record DraftSnapshot(
        String currency,
        String customerNote,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal total,
        List<DraftLineSpec> lines,
        UUID slotId
    ) {
    }

    private record DraftLineSpec(
        Product product,
        ProductVariant variant,
        String productName,
        String variantName,
        String sku,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal taxRate,
        BigDecimal lineTax,
        BigDecimal lineTotal,
        String specialInstructions
    ) {
    }

    private record RequirementKey(
        UUID stockItemId,
        UUID orderItemId
    ) {
    }

    private record Requirement(
        OrderItem orderItem,
        StockItem stockItem,
        BigDecimal quantity
    ) {
    }
}