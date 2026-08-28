package com.sup2i.food.order.repository;

import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository
    extends JpaRepository<Order, UUID> {

    Optional<Order>
        findByIdAndOrganization_Id(
            UUID id,
            UUID organizationId
        );

    Optional<Order>
        findByIdAndOrganization_IdAndStudent_Id(
            UUID id,
            UUID organizationId,
            UUID studentId
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o
        from Order o
        where o.id = :orderId
          and o.organization.id = :organizationId
        """)
    Optional<Order> findOwnedByIdForUpdate(
        @Param("orderId")
        UUID orderId,

        @Param("organizationId")
        UUID organizationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o
        from Order o
        where o.id = :orderId
          and o.organization.id = :organizationId
          and o.student.id = :studentId
        """)
    Optional<Order> findStudentOwnedForUpdate(
        @Param("orderId")
        UUID orderId,

        @Param("organizationId")
        UUID organizationId,

        @Param("studentId")
        UUID studentId
    );

    long countByStudent_IdAndStatusIn(
        UUID studentId,
        Collection<OrderStatus> statuses
    );
}