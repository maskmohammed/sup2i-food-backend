package com.sup2i.food.order.repository;

import com.sup2i.food.order.domain.OrderItemMenuSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface OrderItemMenuSelectionRepository
    extends JpaRepository<
        OrderItemMenuSelection,
        UUID
    > {

    @Query("""
        select s
        from OrderItemMenuSelection s
        where s.orderItem.order.id = :orderId
        order by
            s.orderItem.id asc,
            s.id asc
        """)
    List<OrderItemMenuSelection>
        findAllByOrderId(
            @Param("orderId")
            UUID orderId
        );

    @Query("""
        select s
        from OrderItemMenuSelection s
        join fetch s.orderItem oi
        join fetch s.product
        left join fetch s.variant
        where oi.order.id in :orderIds
        order by
            oi.order.id asc,
            oi.id asc,
            s.id asc
        """)
    List<OrderItemMenuSelection> findAllByOrderIds(
        @Param("orderIds")
        Collection<UUID> orderIds
    );
}
