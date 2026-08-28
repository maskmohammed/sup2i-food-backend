package com.sup2i.food.timeslot.repository;

import com.sup2i.food.timeslot.domain.TimeSlotReservation;
import com.sup2i.food.timeslot.domain.TimeSlotReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TimeSlotReservationRepository
    extends JpaRepository<
        TimeSlotReservation,
        UUID
    > {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r
        from TimeSlotReservation r
        where r.order.id = :orderId
          and r.status = com.sup2i.food.timeslot.domain.TimeSlotReservationStatus.ACTIVE
        """)
    Optional<TimeSlotReservation>
        findActiveByOrderForUpdate(
            @Param("orderId")
            UUID orderId
        );

    boolean
        existsByOrder_IdAndStatus(
            UUID orderId,
            TimeSlotReservationStatus status
        );
}
