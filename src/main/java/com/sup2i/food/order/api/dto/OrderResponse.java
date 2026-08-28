package com.sup2i.food.order.api.dto;

import com.sup2i.food.order.domain.OrderPaymentStatus;
import com.sup2i.food.order.domain.OrderSource;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.OrderType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    String orderNumber,
    UUID locationId,
    UUID studentId,
    UUID timeSlotId,
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
    List<StockReservationResponse> reservations,
    String qrToken
) {
}