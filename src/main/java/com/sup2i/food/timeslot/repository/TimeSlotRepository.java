package com.sup2i.food.timeslot.repository;

import com.sup2i.food.timeslot.domain.TimeSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSlotRepository
    extends JpaRepository<
        TimeSlot,
        UUID
    > {

    Optional<TimeSlot>
        findByIdAndLocation_Id(
            UUID id,
            UUID locationId
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select s
        from TimeSlot s
        where s.id = :id
        """)
    Optional<TimeSlot>
        findLockedById(
            @Param("id")
            UUID id
        );

    List<TimeSlot>
        findAllByLocation_IdAndSlotDateOrderByStartTimeAsc(
            UUID locationId,
            LocalDate slotDate
        );
}
