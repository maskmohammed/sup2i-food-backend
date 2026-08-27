package com.sup2i.food.kitchen.repository;

import com.sup2i.food.kitchen.domain.KitchenTicketItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KitchenTicketItemRepository
    extends JpaRepository<
        KitchenTicketItem,
        UUID
    > {

    List<KitchenTicketItem>
        findAllByKitchenTicket_IdOrderByIdAsc(
            UUID kitchenTicketId
        );
}
