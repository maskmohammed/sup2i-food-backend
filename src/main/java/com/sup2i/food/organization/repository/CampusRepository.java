package com.sup2i.food.organization.repository;

import com.sup2i.food.organization.domain.Campus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CampusRepository
    extends JpaRepository<Campus, UUID> {

    Optional<Campus>
        findByIdAndOrganization_Id(
            UUID id,
            UUID organizationId
        );
}