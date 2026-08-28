package com.sup2i.food.menuvote.repository;

import com.sup2i.food.menuvote.domain.MenuVoteSession;
import com.sup2i.food.menuvote.domain.MenuVoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MenuVoteSessionRepository
    extends JpaRepository<MenuVoteSession, UUID> {

    Optional<MenuVoteSession> findByIdAndOrganization_Id(
        UUID id,
        UUID organizationId
    );

    Page<MenuVoteSession> findAllByOrganization_Id(
        UUID organizationId,
        Pageable pageable
    );

    Page<MenuVoteSession> findAllByOrganization_IdAndStatus(
        UUID organizationId,
        MenuVoteStatus status,
        Pageable pageable
    );

    Optional<MenuVoteSession>
        findFirstByOrganization_IdAndStatusAndVoteDeadlineGreaterThanOrderByVoteDeadlineAsc(
            UUID organizationId,
            MenuVoteStatus status,
            OffsetDateTime now
        );
}