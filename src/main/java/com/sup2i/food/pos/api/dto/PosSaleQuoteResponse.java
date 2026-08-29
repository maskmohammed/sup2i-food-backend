package com.sup2i.food.pos.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record PosSaleQuoteResponse(
    BigDecimal subtotal,
    BigDecimal taxTotal,
    BigDecimal discountTotal,
    BigDecimal total,
    String currency,
    List<PosSaleQuoteLineResponse> items
) {
}