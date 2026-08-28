package com.sup2i.food.menuvote.repository;

import com.sup2i.food.menuvote.domain.MenuVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuVoteRepository
    extends JpaRepository<MenuVote, UUID> {

    boolean existsBySession_IdAndStudent_Id(
        UUID sessionId,
        UUID studentId
    );

    long countBySession_IdAndOption_Id(
        UUID sessionId,
        UUID optionId
    );

    List<MenuVote> findAllBySession_Id(
        UUID sessionId
    );
}