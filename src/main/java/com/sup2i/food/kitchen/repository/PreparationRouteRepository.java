package com.sup2i.food.kitchen.repository;

import com.sup2i.food.kitchen.domain.PreparationRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PreparationRouteRepository
    extends JpaRepository<PreparationRoute, UUID> {

    @Query("""
        select r
        from PreparationRoute r
        where r.sourceLocation.id = :sourceLocationId
          and r.active = true
          and (
                r.validFrom is null
                or r.validFrom <= :at
              )
          and (
                r.validTo is null
                or r.validTo > :at
              )
        order by
            r.priority desc,
            r.id asc
        """)
    List<PreparationRoute> findEffectiveForSource(
        @Param("sourceLocationId")
        UUID sourceLocationId,

        @Param("at")
        OffsetDateTime at
    );
}