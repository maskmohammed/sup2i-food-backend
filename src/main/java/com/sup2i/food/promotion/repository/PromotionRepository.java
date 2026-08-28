package com.sup2i.food.promotion.repository;

import com.sup2i.food.promotion.domain.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromotionRepository
    extends JpaRepository<Promotion, UUID> {
}