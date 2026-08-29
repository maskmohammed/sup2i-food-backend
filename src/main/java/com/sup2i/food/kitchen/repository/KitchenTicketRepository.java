package com.sup2i.food.kitchen.repository;

import com.sup2i.food.kitchen.domain.KitchenTicket;
import com.sup2i.food.kitchen.domain.KitchenTicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitchenTicketRepository
    extends JpaRepository<KitchenTicket, UUID> {

    @Query("""
        select t
        from KitchenTicket t
        where t.id = :ticketId
          and t.order.organization.id = :organizationId
        """)
    Optional<KitchenTicket> findOwnedById(
        @Param("ticketId")
        UUID ticketId,

        @Param("organizationId")
        UUID organizationId
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select t
        from KitchenTicket t
        where t.id = :ticketId
          and t.order.organization.id = :organizationId
        """)
    Optional<KitchenTicket> findOwnedByIdForUpdate(
        @Param("ticketId")
        UUID ticketId,

        @Param("organizationId")
        UUID organizationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select t
        from KitchenTicket t
        where t.order.id = :orderId
          and t.order.organization.id = :organizationId
        order by
            t.kitchenLocation.id asc,
            t.id asc
        """)
    List<KitchenTicket> findAllByOrderForUpdate(
        @Param("orderId")
        UUID orderId,

        @Param("organizationId")
        UUID organizationId
    );

    @Query("""
        select t
        from KitchenTicket t
        join fetch t.order o
        where o.organization.id = :organizationId
          and (
                :kitchenLocationId is null
                or t.kitchenLocation.id = :kitchenLocationId
              )
          and t.status in :statuses
        order by
            t.priority desc,
            t.queuedAt asc,
            t.id asc
        """)
    List<KitchenTicket> findQueue(
        @Param("organizationId")
        UUID organizationId,

        @Param("kitchenLocationId")
        UUID kitchenLocationId,

        @Param("statuses")
        Collection<KitchenTicketStatus> statuses
    );

    List<KitchenTicket>
        findAllByOrder_IdOrderByKitchenLocation_IdAscIdAsc(
            UUID orderId
        );
}
