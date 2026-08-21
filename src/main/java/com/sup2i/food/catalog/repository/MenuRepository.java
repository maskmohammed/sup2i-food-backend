package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MenuRepository
    extends JpaRepository<Menu, UUID> {

    Optional<Menu> findByProduct_Id(
        UUID productId
    );
}