package com.sup2i.food.payment.service;

import com.sup2i.food.order.domain.Order;
import com.sup2i.food.payment.domain.Payment;
import com.sup2i.food.payment.domain.PaymentMethod;
import com.sup2i.food.payment.exception.PaymentConflictException;
import com.sup2i.food.payment.exception.PaymentNotFoundException;
import com.sup2i.food.payment.exception.PaymentValidationException;
import com.sup2i.food.payment.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(
        PaymentRepository paymentRepository
    ) {
        this.paymentRepository =
            paymentRepository;
    }

    @Transactional
    public Payment recordCompletedPayment(
        Order order,
        PaymentMethod method,
        BigDecimal amount,
        OffsetDateTime paidAt
    ) {

        if (
            amount == null
            || amount.signum() <= 0
        ) {
            throw new PaymentValidationException(
                "Payment amount must be positive."
            );
        }

        if (
            paymentRepository
                .existsByOrder_Id(
                    order.getId()
                )
        ) {
            throw new PaymentConflictException(
                "A payment already exists for this order."
            );
        }

        String idempotencyKey =
            "ORDER-PAY-"
                + order.getId();

        Payment payment =
            new Payment(
                order,
                method,
                amount,
                order.getCurrency(),
                idempotencyKey,
                paidAt
            );

        try {

            return paymentRepository
                .saveAndFlush(payment);

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new PaymentConflictException(
                "Payment conflicts with an existing resource."
            );
        }
    }

    @Transactional(readOnly = true)
    public Payment getByOrderId(
        UUID orderId
    ) {

        return paymentRepository
            .findByOrder_Id(orderId)
            .orElseThrow(() ->
                new PaymentNotFoundException(
                    "Payment does not exist for this order."
                )
            );
    }
}
