package com.sup2i.food.grouporder.api.dto;

import java.time.OffsetDateTime;

public record CreateGroupOrderCommand(
    String joinCode,
    OffsetDateTime closesAt
) {
}