package com.sup2i.food.identity.repository;

import com.sup2i.food.identity.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findAllByOrganization_Id(
        UUID organizationId,
        Pageable pageable
    );

    Optional<User> findByIdAndOrganization_Id(
        UUID id,
        UUID organizationId
    );
}