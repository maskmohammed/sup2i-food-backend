package com.sup2i.food.subscription.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ActivateSubscriptionRequest(

    @NotBlank
    @Size(max = 160)
    String paymentReference,

    @DecimalMin("0.00")
    BigDecimal administrativePaymentAmount
) {
}
