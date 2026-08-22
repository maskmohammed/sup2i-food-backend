package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.StockLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLocationRepository
    extends JpaRepository<StockLocation, UUID> {

    List<StockLocation>
        findAllByLocation_Campus_Organization_IdOrderByNameAsc(
            UUID organizationId
        );

    Optional<StockLocation>
        findByIdAndLocation_Campus_Organization_Id(
            UUID id,
            UUID organizationId
        );
}