package com.sup2i.food.kitchen;

import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.inventory.domain.InventoryMovement;
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
import com.sup2i.food.kitchen.service.PreparedStockConsumptionService;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.StockReservation;
import com.sup2i.food.order.domain.StockReservationStatus;
import com.sup2i.food.order.repository.StockReservationRepository;
import com.sup2i.food.organization.domain.Campus;
import com.sup2i.food.organization.domain.Location;
import com.sup2i.food.organization.domain.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreparedStockConsumptionServiceTest {

    @Mock
    private StockReservationRepository reservationRepository;

    @Mock
    private StockBalanceRepository balanceRepository;

    @Mock
    private StockLotRepository lotRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    @Mock
    private InventoryMovementLotRepository movementLotRepository;

    @Mock
    private InventoryAlertService inventoryAlertService;

    private PreparedStockConsumptionService service;

    @BeforeEach
    void setUp() {
        service =
            new PreparedStockConsumptionService(
                reservationRepository,
                balanceRepository,
                lotRepository,
                movementRepository,
                movementLotRepository,
                inventoryAlertService
            );
    }

    @Test
    void consumesReservedPreparedStockFefoAndCreatesRecipeMovement() {
        Fixture fixture =
            fixture(
                new BigDecimal(
                    "3.000"
                )
            );

        StockLot firstLot =
            lot(
                "2.000"
            );

        StockLot secondLot =
            lot(
                "5.000"
            );

        stubCommon(
            fixture,
            List.of(
                firstLot,
                secondLot
            )
        );

        when(
            movementRepository
                .saveAndFlush(
                    any(
                        InventoryMovement.class
                    )
                )
        ).thenAnswer(invocation -> {
            InventoryMovement movement =
                invocation.getArgument(0);

            ReflectionTestUtils.setField(
                movement,
                "id",
                UUID.randomUUID()
            );

            return movement;
        });

        boolean consumed =
            service.consumePreparedReservations(
                fixture.order(),
                fixture.actor(),
                fixture.at()
            );

        assertThat(consumed)
            .isTrue();

        assertThat(
            fixture.balance()
                .getPhysicalQuantity()
        ).isEqualByComparingTo(
            "7.000"
        );

        assertThat(
            fixture.balance()
                .getReservedQuantity()
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            fixture.balance()
                .getAvailableQuantity()
        ).isEqualByComparingTo(
            "7.000"
        );

        verify(firstLot)
            .consume(
                new BigDecimal(
                    "2.000"
                )
            );

        verify(secondLot)
            .consume(
                new BigDecimal(
                    "1.000"
                )
            );

        verify(fixture.reservation())
            .consume(
                fixture.at()
            );

        ArgumentCaptor<InventoryMovement> movementCaptor =
            ArgumentCaptor.forClass(
                InventoryMovement.class
            );

        verify(movementRepository)
            .saveAndFlush(
                movementCaptor.capture()
            );

        InventoryMovement movement =
            movementCaptor.getValue();

        assertThat(
            movement.getMovementType()
        ).isEqualTo(
            InventoryMovementType.RECIPE_CONSUMPTION
        );

        assertThat(
            movement.getPhysicalDelta()
        ).isEqualByComparingTo(
            "-3.000"
        );

        assertThat(
            movement.getReservedDelta()
        ).isEqualByComparingTo(
            "-3.000"
        );

        assertThat(
            movement.getUnit()
        ).isEqualTo(
            MeasurementUnit.GRAM
        );

        assertThat(
            movement.getReferenceType()
        ).isEqualTo(
            "ORDER"
        );

        assertThat(
            movement.getReferenceId()
        ).isEqualTo(
            fixture.orderId()
        );

        assertThat(
            movement.getPerformedBy()
        ).isSameAs(
            fixture.actor()
        );

        verify(movementLotRepository)
            .saveAll(
                any()
            );

        verify(inventoryAlertService)
            .reconcileOrganization(
                fixture.organizationId()
            );
    }

    @Test
    void insufficientFefoLotsFailBeforeAnyBusinessMutation() {
        Fixture fixture =
            fixture(
                new BigDecimal(
                    "3.000"
                )
            );

        StockLot shortLot =
            lot(
                "2.000"
            );

        stubCommon(
            fixture,
            List.of(
                shortLot
            )
        );

        assertThatThrownBy(() ->
            service.consumePreparedReservations(
                fixture.order(),
                fixture.actor(),
                fixture.at()
            )
        )
            .isInstanceOf(
                KitchenConflictException.class
            )
            .hasMessageContaining(
                "do not contain enough"
            );

        assertThat(
            fixture.balance()
                .getPhysicalQuantity()
        ).isEqualByComparingTo(
            "10.000"
        );

        assertThat(
            fixture.balance()
                .getReservedQuantity()
        ).isEqualByComparingTo(
            "3.000"
        );

        verify(shortLot, never())
            .consume(
                any(
                    BigDecimal.class
                )
            );

        verify(fixture.reservation(), never())
            .consume(
                any(
                    OffsetDateTime.class
                )
            );

        verify(movementRepository, never())
            .saveAndFlush(
                any(
                    InventoryMovement.class
                )
            );

        verifyNoInteractions(
            inventoryAlertService
        );
    }

    @Test
    void orderWithoutPreparedReservationsIsValidNoOp() {
        Fixture fixture =
            fixture(
                new BigDecimal(
                    "3.000"
                )
            );

        when(
            reservationRepository
                .findActiveByOrderForUpdate(
                    fixture.orderId()
                )
        ).thenReturn(
            List.of()
        );

        boolean consumed =
            service.consumePreparedReservations(
                fixture.order(),
                fixture.actor(),
                fixture.at()
            );

        assertThat(consumed)
            .isFalse();

        verifyNoInteractions(
            balanceRepository,
            lotRepository,
            movementRepository,
            movementLotRepository,
            inventoryAlertService
        );
    }

    private void stubCommon(
        Fixture fixture,
        List<StockLot> lots
    ) {
        when(
            reservationRepository
                .findActiveByOrderForUpdate(
                    fixture.orderId()
                )
        ).thenReturn(
            List.of(
                fixture.reservation()
            )
        );

        when(
            movementRepository
                .findByStockItem_IdAndStockLocation_IdAndMovementTypeAndReferenceTypeAndReferenceId(
                    fixture.stockItemId(),
                    fixture.stockLocationId(),
                    InventoryMovementType.RECIPE_CONSUMPTION,
                    "ORDER",
                    fixture.orderId()
                )
        ).thenReturn(
            Optional.empty()
        );

        when(
            balanceRepository
                .findLockedById(
                    any(
                        StockBalanceId.class
                    )
                )
        ).thenReturn(
            Optional.of(
                fixture.balance()
            )
        );

        when(
            lotRepository
                .findConsumableLotsForUpdate(
                    fixture.stockItemId(),
                    fixture.stockLocationId()
                )
        ).thenReturn(
            lots
        );
    }

    private Fixture fixture(
        BigDecimal quantity
    ) {
        UUID organizationId =
            UUID.randomUUID();

        UUID orderId =
            UUID.randomUUID();

        UUID stockItemId =
            UUID.randomUUID();

        UUID stockLocationId =
            UUID.randomUUID();

        OffsetDateTime at =
            OffsetDateTime.parse(
                "2026-08-29T10:00:00+01:00"
            );

        Organization organization =
            mock(
                Organization.class
            );

        lenient().when(
            organization.getId()
        ).thenReturn(
            organizationId
        );

        User actor =
            mock(
                User.class
            );

        lenient().when(
            actor.getOrganization()
        ).thenReturn(
            organization
        );

        Order order =
            mock(
                Order.class
            );

        lenient().when(
            order.getId()
        ).thenReturn(
            orderId
        );

        lenient().when(
            order.getOrganization()
        ).thenReturn(
            organization
        );

        lenient().when(
            order.getStatus()
        ).thenReturn(
            OrderStatus.QUEUED
        );

        lenient().when(
            order.getOrderNumber()
        ).thenReturn(
            "ORD-KITCHEN-TEST"
        );

        Product product =
            mock(
                Product.class
            );

        lenient().when(
            product.isPrepared()
        ).thenReturn(
            true
        );

        OrderItem orderItem =
            mock(
                OrderItem.class
            );

        lenient().when(
            orderItem.getOrder()
        ).thenReturn(
            order
        );

        lenient().when(
            orderItem.getProduct()
        ).thenReturn(
            product
        );

        Ingredient ingredient =
            mock(
                Ingredient.class
            );

        StockItem stockItem =
            mock(
                StockItem.class
            );

        lenient().when(
            stockItem.getId()
        ).thenReturn(
            stockItemId
        );

        lenient().when(
            stockItem.getOrganization()
        ).thenReturn(
            organization
        );

        lenient().when(
            stockItem.getIngredient()
        ).thenReturn(
            ingredient
        );

        lenient().when(
            stockItem.getBaseUnit()
        ).thenReturn(
            MeasurementUnit.GRAM
        );

        Campus campus =
            mock(
                Campus.class
            );

        lenient().when(
            campus.getOrganization()
        ).thenReturn(
            organization
        );

        Location location =
            mock(
                Location.class
            );

        lenient().when(
            location.getCampus()
        ).thenReturn(
            campus
        );

        StockLocation stockLocation =
            mock(
                StockLocation.class
            );

        lenient().when(
            stockLocation.getId()
        ).thenReturn(
            stockLocationId
        );

        lenient().when(
            stockLocation.getLocation()
        ).thenReturn(
            location
        );

        StockReservation reservation =
            mock(
                StockReservation.class
            );

        lenient().when(
            reservation.getStatus()
        ).thenReturn(
            StockReservationStatus.ACTIVE
        );

        lenient().when(
            reservation.getOrder()
        ).thenReturn(
            order
        );

        lenient().when(
            reservation.getOrderItem()
        ).thenReturn(
            orderItem
        );

        lenient().when(
            reservation.getStockItem()
        ).thenReturn(
            stockItem
        );

        lenient().when(
            reservation.getStockLocation()
        ).thenReturn(
            stockLocation
        );

        lenient().when(
            reservation.getQuantity()
        ).thenReturn(
            quantity
        );

        StockBalance balance =
            new StockBalance(
                stockItem,
                stockLocation
            );

        balance.applyPhysicalDelta(
            new BigDecimal(
                "10.000"
            )
        );

        balance.reserve(
            quantity
        );

        return new Fixture(
            organizationId,
            orderId,
            stockItemId,
            stockLocationId,
            at,
            actor,
            order,
            stockItem,
            stockLocation,
            reservation,
            balance
        );
    }

    private StockLot lot(
        String quantity
    ) {
        StockLot lot =
            mock(
                StockLot.class
            );

        lenient().when(
            lot.getId()
        ).thenReturn(
            UUID.randomUUID()
        );

        lenient().when(
            lot.getQuantityRemaining()
        ).thenReturn(
            new BigDecimal(
                quantity
            )
        );

        return lot;
    }

    private record Fixture(
        UUID organizationId,
        UUID orderId,
        UUID stockItemId,
        UUID stockLocationId,
        OffsetDateTime at,
        User actor,
        Order order,
        StockItem stockItem,
        StockLocation stockLocation,
        StockReservation reservation,
        StockBalance balance
    ) {
    }
}
