package com.sup2i.food.subscription.repository;

import com.sup2i.food.subscription.domain.FoodPassEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FoodPassEventRepository
    extends JpaRepository<FoodPassEvent, UUID> {

    List<FoodPassEvent>
        findAllByFoodPass_IdOrderByCreatedAtAsc(
            UUID foodPassId
        );
}