package com.sup2i.food.audit.repository;

import com.sup2i.food.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository
    extends JpaRepository<AuditLog, UUID> {
}