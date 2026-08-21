package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.ProductLocationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductLocationSettingRepository
    extends JpaRepository<ProductLocationSetting, UUID> {

    Optional<ProductLocationSetting>
        findByProduct_IdAndLocation_Id(
            UUID productId,
            UUID locationId
        );

    List<ProductLocationSetting>
        findAllByProduct_IdOrderByLocation_NameAsc(
            UUID productId
        );
}