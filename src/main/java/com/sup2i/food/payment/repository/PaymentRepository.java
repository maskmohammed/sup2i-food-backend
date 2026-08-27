package com.sup2i.food.payment.repository;

import com.sup2i.food.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository
    extends JpaRepository<Payment, UUID> {

    boolean existsByOrder_Id(
        UUID orderId
    );

    Optional<Payment>
        findByOrder_Id(
            UUID orderId
        );
}
