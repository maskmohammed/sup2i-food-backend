package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.ProductPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductPriceHistoryRepository
    extends JpaRepository<
        ProductPriceHistory,
        UUID
    > {
}