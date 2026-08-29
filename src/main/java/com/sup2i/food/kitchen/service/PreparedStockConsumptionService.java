package com.sup2i.food.kitchen.service;

import com.sup2i.food.identity.domain.User;
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
import com.sup2i.food.inventory.repository.StockLotRepository;
import com.sup2i.food.inventory.service.InventoryAlertService;
import com.sup2i.food.kitchen.exception.KitchenConflictException;
import com.sup2i.food.kitchen.exception.KitchenErrorCode;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.StockReservation;
import com.sup2i.food.order.domain.StockReservationStatus;
import com.sup2i.food.order.repository.StockReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class PreparedStockConsumptionService {

    private static final String ORDER_REFERENCE =
        "ORDER";

    private static final String MOVEMENT_REASON =
        "Kitchen recipe consumption";

    private final StockReservationRepository
        reservationRepository;

    private final StockBalanceRepository
        balanceRepository;

    private final StockLotRepository
        lotRepository;

    private final InventoryMovementRepository
        movementRepository;

    private final InventoryMovementLotRepository
        movementLotRepository;

    private final InventoryAlertService
        inventoryAlertService;

    public PreparedStockConsumptionService(
        StockReservationRepository reservationRepository,
        StockBalanceRepository balanceRepository,
        StockLotRepository lotRepository,
        InventoryMovementRepository movementRepository,
        InventoryMovementLotRepository movementLotRepository,
        InventoryAlertService inventoryAlertService
    ) {
        this.reservationRepository =
            reservationRepository;

        this.balanceRepository =
            balanceRepository;

        this.lotRepository =
            lotRepository;

        this.movementRepository =
            movementRepository;

        this.movementLotRepository =
            movementLotRepository;

        this.inventoryAlertService =
            inventoryAlertService;
    }

    @Transactional(
        propagation = Propagation.MANDATORY
    )
    public boolean consumePreparedReservations(
        Order order,
        User actor,
        OffsetDateTime at
    ) {
        Objects.requireNonNull(
            order,
            "order"
        );

        Objects.requireNonNull(
            actor,
            "actor"
        );

        Objects.requireNonNull(
            at,
            "at"
        );

        UUID organizationId =
            Objects.requireNonNull(
                order.getOrganization(),
                "order.organization"
            ).getId();

        UUID actorOrganizationId =
            Objects.requireNonNull(
                actor.getOrganization(),
                "actor.organization"
            ).getId();

        if (
            !organizationId.equals(
                actorOrganizationId
            )
        ) {
            throw concurrentModification(
                "Kitchen actor and order organization mismatch."
            );
        }

        if (
            order.getStatus()
                != OrderStatus.QUEUED
        ) {
            throw new KitchenConflictException(
                KitchenErrorCode.INVALID_ORDER_STATUS,
                "Prepared stock can only be consumed while the order is queued."
            );
        }

        List<StockReservation> reservations =
            reservationRepository
                .findActiveByOrderForUpdate(
                    order.getId()
                );

        /*
         * Valid case for an order that has no prepared
         * ingredient reservation.
         */
        if (reservations.isEmpty()) {
            return false;
        }

        /*
         * Deterministic order:
         *
         * stockItemId
         * then stockLocationId.
         *
         * This keeps all later balance / lot lock ordering
         * stable across concurrent transactions.
         */
        TreeMap<StockKey, MutableGroup> groups =
            new TreeMap<>();

        for (
            StockReservation reservation
            : reservations
        ) {
            validateReservation(
                order,
                organizationId,
                reservation
            );

            StockKey key =
                new StockKey(
                    reservation
                        .getStockItem()
                        .getId(),
                    reservation
                        .getStockLocation()
                        .getId()
                );

            MutableGroup group =
                groups.get(
                    key
                );

            if (group == null) {

                group =
                    new MutableGroup(
                        reservation
                            .getStockItem(),
                        reservation
                            .getStockLocation()
                    );

                groups.put(
                    key,
                    group
                );
            }

            group.add(
                reservation
            );
        }

        /*
         * PHASE A — LOCK + VALIDATE + PLAN.
         *
         * All stock scopes are validated before the first
         * business quantity is mutated.
         */
        List<ConsumptionPlan> plans =
            new ArrayList<>(
                groups.size()
            );

        for (
            MutableGroup group
            : groups.values()
        ) {
            plans.add(
                planConsumption(
                    order,
                    group
                )
            );
        }

        /*
         * PHASE B — APPLY.
         */
        applyPlans(
            order,
            actor,
            at,
            plans
        );

        /*
         * Exactly once after successful physical stock
         * mutation, following the existing inventory/payment
         * reconciliation pattern.
         */
        inventoryAlertService
            .reconcileOrganization(
                organizationId
            );

        return true;
    }

    private void validateReservation(
        Order order,
        UUID organizationId,
        StockReservation reservation
    ) {
        if (reservation == null) {
            throw concurrentModification(
                "Kitchen consumption received a null stock reservation."
            );
        }

        if (
            reservation.getStatus()
                != StockReservationStatus.ACTIVE
        ) {
            throw concurrentModification(
                "Kitchen consumption received a non-active stock reservation."
            );
        }

        if (
            reservation.getOrder() == null
            || !order.getId().equals(
                reservation
                    .getOrder()
                    .getId()
            )
        ) {
            throw concurrentModification(
                "Stock reservation does not belong to the queued order."
            );
        }

        OrderItem orderItem =
            reservation.getOrderItem();

        if (
            orderItem == null
            || orderItem.getOrder() == null
            || !order.getId().equals(
                orderItem
                    .getOrder()
                    .getId()
            )
        ) {
            throw concurrentModification(
                "Prepared stock reservation is not linked to the queued order item."
            );
        }

        if (
            orderItem.getProduct() == null
            || !orderItem
                .getProduct()
                .isPrepared()
        ) {
            /*
             * Packaged reservations must already have been
             * consumed at PAID by Payment.
             */
            throw concurrentModification(
                "A packaged reservation cannot remain active when preparation starts."
            );
        }

        StockItem stockItem =
            reservation.getStockItem();

        StockLocation stockLocation =
            reservation.getStockLocation();

        if (
            stockItem == null
            || stockLocation == null
        ) {
            throw concurrentModification(
                "Prepared stock reservation is missing its inventory scope."
            );
        }

        if (
            stockItem.getIngredient()
                == null
        ) {
            throw concurrentModification(
                "Prepared stock reservation must reference an ingredient stock item."
            );
        }

        BigDecimal quantity =
            reservation.getQuantity();

        if (
            quantity == null
            || quantity.signum() <= 0
        ) {
            throw concurrentModification(
                "Prepared stock reservation quantity must be positive."
            );
        }

        if (
            stockItem.getOrganization()
                == null
            || !organizationId.equals(
                stockItem
                    .getOrganization()
                    .getId()
            )
        ) {
            throw concurrentModification(
                "Prepared stock item belongs to another organization."
            );
        }

        if (
            stockLocation.getLocation()
                == null
            || stockLocation
                .getLocation()
                .getCampus()
                == null
            || stockLocation
                .getLocation()
                .getCampus()
                .getOrganization()
                == null
        ) {
            throw concurrentModification(
                "Prepared stock location has an invalid organization chain."
            );
        }

        UUID locationOrganizationId =
            stockLocation
                .getLocation()
                .getCampus()
                .getOrganization()
                .getId();

        if (
            !organizationId.equals(
                locationOrganizationId
            )
        ) {
            throw concurrentModification(
                "Prepared stock location belongs to another organization."
            );
        }
    }

    private ConsumptionPlan planConsumption(
        Order order,
        MutableGroup group
    ) {
        StockItem stockItem =
            group.stockItem();

        StockLocation stockLocation =
            group.stockLocation();

        BigDecimal quantity =
            group.quantity();

        /*
         * Defensive invariant.
         *
         * The canonical replay protection remains the Order
         * lock + Order state in KitchenStartService, but an
         * existing movement here means the persistent stock
         * state is already inconsistent with ACTIVE reservations.
         */
        boolean duplicateMovement =
            movementRepository
                .findByStockItem_IdAndStockLocation_IdAndMovementTypeAndReferenceTypeAndReferenceId(
                    stockItem.getId(),
                    stockLocation.getId(),
                    InventoryMovementType.RECIPE_CONSUMPTION,
                    ORDER_REFERENCE,
                    order.getId()
                )
                .isPresent();

        if (duplicateMovement) {
            throw concurrentModification(
                "Recipe consumption already exists for this order and stock scope."
            );
        }

        StockBalance balance =
            balanceRepository
                .findLockedById(
                    new StockBalanceId(
                        stockItem.getId(),
                        stockLocation.getId()
                    )
                )
                .orElseThrow(() ->
                    concurrentModification(
                        "Reserved stock balance does not exist."
                    )
                );

        BigDecimal physical =
            balance.getPhysicalQuantity();

        BigDecimal reserved =
            balance.getReservedQuantity();

        if (
            physical == null
            || reserved == null
            || physical.signum() < 0
            || reserved.signum() < 0
            || reserved.compareTo(
                physical
            ) > 0
        ) {
            throw concurrentModification(
                "Reserved stock balance violates inventory invariants."
            );
        }

        if (
            physical.compareTo(
                quantity
            ) < 0
        ) {
            throw new KitchenConflictException(
                KitchenErrorCode.OUT_OF_STOCK,
                "Insufficient physical ingredient stock for kitchen preparation."
            );
        }

        if (
            reserved.compareTo(
                quantity
            ) < 0
        ) {
            throw concurrentModification(
                "Reserved ingredient quantity is lower than active kitchen reservations."
            );
        }

        /*
         * Repository ordering is FEFO:
         * expiry non-null first,
         * expiresAt,
         * receivedAt,
         * id.
         */
        List<StockLot> lots =
            lotRepository
                .findConsumableLotsForUpdate(
                    stockItem.getId(),
                    stockLocation.getId()
                );

        BigDecimal remaining =
            quantity;

        List<LotAllocation> allocations =
            new ArrayList<>();

        for (
            StockLot lot
            : lots
        ) {
            if (
                remaining.signum()
                    == 0
            ) {
                break;
            }

            BigDecimal available =
                lot.getQuantityRemaining();

            if (
                available == null
                || available.signum() < 0
            ) {
                throw concurrentModification(
                    "Ingredient lot violates remaining quantity invariants."
                );
            }

            if (
                available.signum()
                    == 0
            ) {
                continue;
            }

            BigDecimal take =
                available.min(
                    remaining
                );

            allocations.add(
                new LotAllocation(
                    lot,
                    take
                )
            );

            remaining =
                remaining.subtract(
                    take
                );
        }

        if (
            remaining.signum() > 0
        ) {
            throw new KitchenConflictException(
                KitchenErrorCode.OUT_OF_STOCK,
                "Ingredient lots do not contain enough physical stock for preparation."
            );
        }

        return new ConsumptionPlan(
            stockItem,
            stockLocation,
            balance,
            quantity,
            group.reservations(),
            allocations
        );
    }

    private void applyPlans(
        Order order,
        User actor,
        OffsetDateTime at,
        List<ConsumptionPlan> plans
    ) {
        List<InventoryMovementLot> movementLots =
            new ArrayList<>();

        for (
            ConsumptionPlan plan
            : plans
        ) {
            /*
             * PREPARING:
             *
             * physical -= q
             * reserved -= q
             *
             * available therefore remains unchanged.
             *
             * consumeReserved is the ONLY balance mutation;
             * do not also call applyPhysicalDelta.
             */
            plan.balance()
                .consumeReserved(
                    plan.quantity()
                );

            /*
             * A movement is flushed before constructing
             * InventoryMovementLot because its composite key
             * requires movement.getId().
             */
            InventoryMovement movement =
                movementRepository
                    .saveAndFlush(
                        new InventoryMovement(
                            plan.stockItem(),
                            plan.stockLocation(),
                            InventoryMovementType.RECIPE_CONSUMPTION,
                            plan.quantity().negate(),
                            plan.quantity().negate(),
                            plan.stockItem().getBaseUnit(),
                            null,
                            ORDER_REFERENCE,
                            order.getId(),
                            MOVEMENT_REASON,
                            order.getOrderNumber(),
                            actor
                        )
                    );

            for (
                LotAllocation allocation
                : plan.allocations()
            ) {
                allocation
                    .lot()
                    .consume(
                        allocation.quantity()
                    );

                movementLots.add(
                    new InventoryMovementLot(
                        movement,
                        allocation.lot(),
                        allocation
                            .quantity()
                            .negate()
                    )
                );
            }

            for (
                StockReservation reservation
                : plan.reservations()
            ) {
                reservation.consume(
                    at
                );
            }
        }

        if (!movementLots.isEmpty()) {
            movementLotRepository
                .saveAll(
                    movementLots
                );
        }

        /*
         * Explicit flush before alert reconciliation.
         */
        reservationRepository.flush();
        balanceRepository.flush();
        lotRepository.flush();
        movementRepository.flush();

        if (!movementLots.isEmpty()) {
            movementLotRepository.flush();
        }
    }

    private KitchenConflictException
        concurrentModification(
            String message
        ) {

        return new KitchenConflictException(
            KitchenErrorCode.CONCURRENT_MODIFICATION,
            message
        );
    }

    private record StockKey(
        UUID stockItemId,
        UUID stockLocationId
    ) implements Comparable<StockKey> {

        @Override
        public int compareTo(
            StockKey other
        ) {
            int itemComparison =
                stockItemId.compareTo(
                    other.stockItemId
                );

            if (itemComparison != 0) {
                return itemComparison;
            }

            return stockLocationId
                .compareTo(
                    other.stockLocationId
                );
        }
    }

    private static final class MutableGroup {

        private final StockItem stockItem;

        private final StockLocation stockLocation;

        private BigDecimal quantity =
            BigDecimal.ZERO;

        private final List<StockReservation>
            reservations =
                new ArrayList<>();

        private MutableGroup(
            StockItem stockItem,
            StockLocation stockLocation
        ) {
            this.stockItem =
                stockItem;

            this.stockLocation =
                stockLocation;
        }

        private void add(
            StockReservation reservation
        ) {
            quantity =
                quantity.add(
                    reservation.getQuantity()
                );

            reservations.add(
                reservation
            );
        }

        private StockItem stockItem() {
            return stockItem;
        }

        private StockLocation stockLocation() {
            return stockLocation;
        }

        private BigDecimal quantity() {
            return quantity;
        }

        private List<StockReservation>
            reservations() {

            return List.copyOf(
                reservations
            );
        }
    }

    private record LotAllocation(
        StockLot lot,
        BigDecimal quantity
    ) {
    }

    private record ConsumptionPlan(
        StockItem stockItem,
        StockLocation stockLocation,
        StockBalance balance,
        BigDecimal quantity,
        List<StockReservation> reservations,
        List<LotAllocation> allocations
    ) {

        private ConsumptionPlan {

            reservations =
                List.copyOf(
                    reservations
                );

            allocations =
                List.copyOf(
                    allocations
                );
        }
    }
}
