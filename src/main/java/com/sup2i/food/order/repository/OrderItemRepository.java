package com.sup2i.food.order.repository;

import com.sup2i.food.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderItemRepository
    extends JpaRepository<OrderItem, UUID> {

    List<OrderItem>
        findAllByOrder_IdOrderByIdAsc(
            UUID orderId
        );

    Optional<OrderItem>
        findByIdAndOrder_Organization_Id(
            UUID id,
            UUID organizationId
        );

    void deleteAllByOrder_Id(
        UUID orderId
    );
}