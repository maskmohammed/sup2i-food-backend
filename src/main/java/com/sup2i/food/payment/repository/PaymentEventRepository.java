package com.sup2i.food.payment.repository;

import com.sup2i.food.payment.domain.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentEventRepository
    extends JpaRepository<
        PaymentEvent,
        UUID
    > {

    List<PaymentEvent>
        findAllByPayment_IdOrderByOccurredAtAsc(
            UUID paymentId
        );
}