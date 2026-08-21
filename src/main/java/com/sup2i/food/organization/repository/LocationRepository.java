package com.sup2i.food.organization.repository;

import com.sup2i.food.organization.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LocationRepository
    extends JpaRepository<Location, UUID> {

    Optional<Location>
        findByIdAndCampus_Organization_Id(
            UUID id,
            UUID organizationId
        );
}