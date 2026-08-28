package com.sup2i.food.timeslot.service;

import com.sup2i.food.order.domain.Order;
import com.sup2i.food.timeslot.api.dto.TimeSlotResponse;
import com.sup2i.food.timeslot.domain.TimeSlot;
import com.sup2i.food.timeslot.domain.TimeSlotReservation;
import com.sup2i.food.timeslot.exception.TimeSlotConflictException;
import com.sup2i.food.timeslot.exception.TimeSlotNotFoundException;
import com.sup2i.food.timeslot.repository.TimeSlotReservationRepository;
import com.sup2i.food.timeslot.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final TimeSlotReservationRepository timeSlotReservationRepository;

    public TimeSlotService(
        TimeSlotRepository timeSlotRepository,
        TimeSlotReservationRepository timeSlotReservationRepository
    ) {
        this.timeSlotRepository =
            timeSlotRepository;

        this.timeSlotReservationRepository =
            timeSlotReservationRepository;
    }

    @Transactional(readOnly = true)
    public List<TimeSlotResponse> listAvailable(
        UUID locationId,
        LocalDate date
    ) {

        LocalDateTime now =
            LocalDateTime.now();

        return timeSlotRepository
            .findAllByLocation_IdAndSlotDateOrderByStartTimeAsc(
                locationId,
                date
            )
            .stream()
            .filter(slot ->
                !slot.isClosed()
                && !slot.isPast(now)
            )
            .map(this::response)
            .toList();
    }

    @Transactional(readOnly = true)
    public TimeSlot validateSelectable(
        UUID timeSlotId,
        UUID locationId
    ) {

        TimeSlot slot =
            timeSlotRepository
                .findByIdAndLocation_Id(
                    timeSlotId,
                    locationId
                )
                .orElseThrow(() ->
                    new TimeSlotNotFoundException(
                        "Time slot does not exist for this location."
                    )
                );

        if (slot.isClosed()) {
            throw new TimeSlotConflictException(
                "Time slot is closed."
            );
        }

        if (
            slot.isPast(
                LocalDateTime.now()
            )
        ) {
            throw new TimeSlotConflictException(
                "Time slot has already passed."
            );
        }

        return slot;
    }

    @Transactional
    public void reserve(
        Order order
    ) {

        UUID slotId =
            order.getSlotId();

        TimeSlot slot =
            timeSlotRepository
                .findLockedById(
                    slotId
                )
                .orElseThrow(() ->
                    new TimeSlotNotFoundException(
                        "Time slot does not exist."
                    )
                );

        if (slot.isClosed()) {
            throw new TimeSlotConflictException(
                "Time slot is closed."
            );
        }

        if (
            slot.isPast(
                LocalDateTime.now()
            )
        ) {
            throw new TimeSlotConflictException(
                "Time slot has already passed."
            );
        }

        if (!slot.hasCapacity()) {
            throw new TimeSlotConflictException(
                "Time slot is full."
            );
        }

        slot.reserve();

        timeSlotRepository
            .saveAndFlush(slot);

        timeSlotReservationRepository
            .saveAndFlush(
                new TimeSlotReservation(
                    slot,
                    order
                )
            );
    }

    @Transactional
    public void release(
        Order order,
        boolean expired
    ) {

        TimeSlotReservation reservation =
            timeSlotReservationRepository
                .findActiveByOrderForUpdate(
                    order.getId()
                )
                .orElse(null);

        if (reservation == null) {
            return;
        }

        TimeSlot slot =
            timeSlotRepository
                .findLockedById(
                    reservation
                        .getTimeSlot()
                        .getId()
                )
                .orElseThrow(() ->
                    new TimeSlotNotFoundException(
                        "Time slot does not exist."
                    )
                );

        slot.release();

        timeSlotRepository
            .saveAndFlush(slot);

        OffsetDateTime now =
            OffsetDateTime.now();

        if (expired) {
            reservation.expire(
                now,
                "Payment window expired."
            );
        } else {
            reservation.release(
                now,
                "Order cancelled."
            );
        }

        timeSlotReservationRepository
            .saveAndFlush(reservation);
    }

    private TimeSlotResponse response(
        TimeSlot slot
    ) {

        return new TimeSlotResponse(
            slot.getId(),
            slot.getLocation()
                .getId(),
            slot.getSlotDate(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getCapacity(),
            slot.getReservedCount(),
            slot.getRemainingCapacity(),
            slot.getStatus()
                .name()
        );
    }
}
