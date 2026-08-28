package com.sup2i.food.promotion.repository;

import com.sup2i.food.promotion.domain.LoyaltyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoyaltyTransactionRepository
    extends JpaRepository<LoyaltyTransaction, UUID> {
}