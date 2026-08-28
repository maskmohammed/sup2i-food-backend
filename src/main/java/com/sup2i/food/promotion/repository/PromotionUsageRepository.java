package com.sup2i.food.promotion.repository;

import com.sup2i.food.promotion.domain.PromotionUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromotionUsageRepository
    extends JpaRepository<PromotionUsage, UUID> {

    long countByPromotion_Id(
        UUID promotionId
    );
}