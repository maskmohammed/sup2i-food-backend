package com.sup2i.food.payment.repository;

import com.sup2i.food.payment.domain.Payment;
import com.sup2i.food.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository
    extends JpaRepository<
        Payment,
        UUID
    > {

    Optional<Payment>
        findByIdempotencyKey(
            String idempotencyKey
        );

    List<Payment>
        findAllByOrder_IdOrderByCreatedAtAsc(
            UUID orderId
        );

    boolean existsByOrder_IdAndStatus(
        UUID orderId,
        PaymentStatus status
    );
}