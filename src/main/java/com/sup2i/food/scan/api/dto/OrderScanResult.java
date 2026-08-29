package com.sup2i.food.scan.api.dto;

import com.sup2i.food.order.api.dto.OrderResponse;

public record OrderScanResult(
    String type,
    OrderResponse order
) implements ScanResponse {

    public OrderScanResult(
        OrderResponse order
    ) {
        this(
            "ORDER",
            order
        );
    }
}