package com.sup2i.food.pos.api.dto;

import com.sup2i.food.pos.domain.PosSessionStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PosSessionResponse(
    UUID id,
    UUID terminalId,
    UUID cashierId,
    PosSessionStatus status,
    BigDecimal openingCash,
    BigDecimal expectedCash,
    BigDecimal countedCash,
    BigDecimal cardTotal,
    BigDecimal difference
) {

    public PosSessionResponse(
        UUID id,
        UUID terminalId,
        UUID cashierId,
        PosSessionStatus status,
        BigDecimal openingCash,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal cardTotal
    ) {
        this(
            id,
            terminalId,
            cashierId,
            status,
            openingCash,
            expectedCash,
            countedCash,
            cardTotal,
            null
        );
    }
}