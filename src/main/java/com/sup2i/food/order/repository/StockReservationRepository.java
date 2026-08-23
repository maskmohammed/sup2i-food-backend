package com.sup2i.food.order.repository;

import com.sup2i.food.order.domain.StockReservation;
import com.sup2i.food.order.domain.StockReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StockReservationRepository
    extends JpaRepository<
        StockReservation,
        UUID
    > {

    List<StockReservation>
        findAllByOrder_IdOrderByCreatedAtAsc(
            UUID orderId
        );

    boolean existsByOrder_IdAndStatus(
        UUID orderId,
        StockReservationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r
        from StockReservation r
        where r.order.id = :orderId
          and r.status = com.sup2i.food.order.domain.StockReservationStatus.ACTIVE
        order by
            r.stockItem.id asc,
            r.stockLocation.id asc,
            r.id asc
        """)
    List<StockReservation>
        findActiveByOrderForUpdate(
            @Param("orderId")
            UUID orderId
        );
}