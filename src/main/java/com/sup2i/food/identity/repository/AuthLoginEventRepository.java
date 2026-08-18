package com.sup2i.food.identity.repository;

import com.sup2i.food.identity.domain.AuthLoginEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthLoginEventRepository
    extends JpaRepository<AuthLoginEvent, UUID> {
}
