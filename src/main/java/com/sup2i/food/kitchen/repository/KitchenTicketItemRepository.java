package com.sup2i.food.kitchen.repository;

import com.sup2i.food.kitchen.domain.KitchenTicketItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface KitchenTicketItemRepository
    extends JpaRepository<KitchenTicketItem, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select i
        from KitchenTicketItem i
        where i.kitchenTicket.id = :ticketId
        order by i.id asc
        """)
    List<KitchenTicketItem> findAllByTicketForUpdate(
        @Param("ticketId")
        UUID ticketId
    );

    List<KitchenTicketItem>
        findAllByKitchenTicket_IdOrderByIdAsc(
            UUID ticketId
        );

    @Query("""
        select i
        from KitchenTicketItem i
        join fetch i.kitchenTicket t
        join fetch i.orderItem oi
        join fetch oi.product
        left join fetch oi.variant
        where t.id in :ticketIds
        order by
            t.id asc,
            i.id asc
        """)
    List<KitchenTicketItem> findAllByTicketIds(
        @Param("ticketIds")
        Collection<UUID> ticketIds
    );
}
