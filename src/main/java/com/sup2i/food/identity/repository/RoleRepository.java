package com.sup2i.food.identity.repository;

import com.sup2i.food.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);
}
