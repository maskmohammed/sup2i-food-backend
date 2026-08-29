package com.sup2i.food.order.service;

import java.math.BigDecimal;
import java.util.UUID;

public record PosDirectQuoteLine(
    UUID productId,
    UUID variantId,
    String productName,
    String variantName,
    String sku,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal discountAmount,
    BigDecimal lineTotal,
    BigDecimal taxRate,
    BigDecimal lineTax,
    String specialInstructions
) {
}