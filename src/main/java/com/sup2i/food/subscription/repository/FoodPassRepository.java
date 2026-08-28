package com.sup2i.food.subscription.repository;

import com.sup2i.food.subscription.domain.FoodPass;
import com.sup2i.food.subscription.domain.FoodPassStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FoodPassRepository
    extends JpaRepository<FoodPass, UUID> {

    Optional<FoodPass>
        findByStudent_IdAndStatus(
            UUID studentId,
            FoodPassStatus status
        );

    List<FoodPass>
        findAllByStudent_IdOrderByIssuedAtDesc(
            UUID studentId
        );

    List<FoodPass>
        findAllByStudent_User_Organization_IdOrderByIssuedAtDesc(
            UUID organizationId
        );

    Optional<FoodPass>
        findByIdAndStudent_User_Organization_Id(
            UUID id,
            UUID organizationId
        );
}