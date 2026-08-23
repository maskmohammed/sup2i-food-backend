package com.sup2i.food.order.repository;

import com.sup2i.food.order.domain.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderStatusHistoryRepository
    extends JpaRepository<
        OrderStatusHistory,
        UUID
    > {

    List<OrderStatusHistory>
        findAllByOrder_IdOrderByCreatedAtAsc(
            UUID orderId
        );
}