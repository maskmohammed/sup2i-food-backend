package com.sup2i.food.order.service;

import java.math.BigDecimal;
import java.util.List;

public record PosDirectQuote(
    BigDecimal subtotal,
    BigDecimal taxTotal,
    BigDecimal discountTotal,
    BigDecimal total,
    String currency,
    List<PosDirectQuoteLine> lines
) {
}