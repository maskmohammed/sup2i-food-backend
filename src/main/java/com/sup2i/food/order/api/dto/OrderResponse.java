package com.sup2i.food.order.api.dto;

import com.sup2i.food.order.domain.OrderPaymentStatus;
import com.sup2i.food.order.domain.OrderSource;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.OrderType;
import com.sup2i.food.slot.api.dto.TimeSlotResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    String orderNumber,
    UUID locationId,
    TimeSlotResponse slot,
    UUID studentId,
    OrderSource source,
    OrderStatus status,
    OrderType orderType,
    OrderPaymentStatus paymentStatus,
    BigDecimal subtotal,
    BigDecimal taxTotal,
    BigDecimal discountTotal,
    BigDecimal total,
    String currency,
    OffsetDateTime paymentExpiresAt,
    QueueEstimateResponse queue,
    String customerNote,
    int version,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<OrderItemResponse> items,
    List<StockReservationResponse> reservations
) {

    /*
     * P2-compatible constructor:
     * slot available, queue omitted.
     */
    public OrderResponse(
        UUID id,
        String orderNumber,
        UUID locationId,
        TimeSlotResponse slot,
        UUID studentId,
        OrderSource source,
        OrderStatus status,
        OrderType orderType,
        OrderPaymentStatus paymentStatus,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal discountTotal,
        BigDecimal total,
        String currency,
        OffsetDateTime paymentExpiresAt,
        String customerNote,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OrderItemResponse> items,
        List<StockReservationResponse> reservations
    ) {
        this(
            id,
            orderNumber,
            locationId,
            slot,
            studentId,
            source,
            status,
            orderType,
            paymentStatus,
            subtotal,
            taxTotal,
            discountTotal,
            total,
            currency,
            paymentExpiresAt,
            null,
            customerNote,
            version,
            createdAt,
            updatedAt,
            items,
            reservations
        );
    }

    /*
     * Pre-P2/POS-compatible constructor:
     * direct POS orders have neither slot nor mobile queue.
     */
    public OrderResponse(
        UUID id,
        String orderNumber,
        UUID locationId,
        UUID studentId,
        OrderSource source,
        OrderStatus status,
        OrderType orderType,
        OrderPaymentStatus paymentStatus,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal discountTotal,
        BigDecimal total,
        String currency,
        OffsetDateTime paymentExpiresAt,
        String customerNote,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OrderItemResponse> items,
        List<StockReservationResponse> reservations
    ) {
        this(
            id,
            orderNumber,
            locationId,
            null,
            studentId,
            source,
            status,
            orderType,
            paymentStatus,
            subtotal,
            taxTotal,
            discountTotal,
            total,
            currency,
            paymentExpiresAt,
            null,
            customerNote,
            version,
            createdAt,
            updatedAt,
            items,
            reservations
        );
    }
}