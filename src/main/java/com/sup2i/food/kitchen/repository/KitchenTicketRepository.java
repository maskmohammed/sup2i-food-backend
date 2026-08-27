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
    extends JpaRepository<
        KitchenTicket,
        UUID
    > {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select t
        from KitchenTicket t
        where t.id = :ticketId
          and t.order.organization.id = :organizationId
        """)
    Optional<KitchenTicket>
        findOwnedByIdForUpdate(
            @Param("ticketId")
            UUID ticketId,

            @Param("organizationId")
            UUID organizationId
        );

    List<KitchenTicket>
        findAllByOrder_Id(
            UUID orderId
        );

    @Query("""
        select t
        from KitchenTicket t
        where t.order.organization.id = :organizationId
          and t.status in :statuses
        order by
            t.priority desc,
            t.queuedAt asc
        """)
    List<KitchenTicket>
        findQueue(
            @Param("organizationId")
            UUID organizationId,

            @Param("statuses")
            Collection<KitchenTicketStatus> statuses
        );
}
