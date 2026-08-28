package com.sup2i.food.promotion.repository;

import com.sup2i.food.promotion.domain.PromotionTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PromotionTargetRepository
    extends JpaRepository<PromotionTarget, UUID> {

    List<PromotionTarget>
        findByPromotion_Id(
            UUID promotionId
        );

    void deleteAllByPromotion_Id(
        UUID promotionId
    );
}