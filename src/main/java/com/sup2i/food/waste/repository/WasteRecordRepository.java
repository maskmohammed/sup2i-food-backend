package com.sup2i.food.waste.repository;

import com.sup2i.food.waste.domain.WasteRecord;
import com.sup2i.food.waste.domain.WasteType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WasteRecordRepository
    extends JpaRepository<WasteRecord, UUID> {

    Optional<WasteRecord>
        findByIdAndOrganization_Id(
            UUID id,
            UUID organizationId
        );

    List<WasteRecord>
        findAllByOrganization_IdOrderByRecordedAtDesc(
            UUID organizationId,
            Pageable pageable
        );

    @Query("""
        select count(w)
        from WasteRecord w
        where w.organization.id = :organizationId
        """)
    long countByOrganization(
        @Param("organizationId")
        UUID organizationId
    );

    @Query("""
        select coalesce(sum(w.quantity), 0)
        from WasteRecord w
        where w.organization.id = :organizationId
        """)
    BigDecimal sumQuantityByOrganization(
        @Param("organizationId")
        UUID organizationId
    );

    @Query("""
        select coalesce(sum(w.estimatedCost), 0)
        from WasteRecord w
        where w.organization.id = :organizationId
        """)
    BigDecimal sumCostByOrganization(
        @Param("organizationId")
        UUID organizationId
    );

    @Query("""
        select w.wasteType, sum(w.quantity), count(w)
        from WasteRecord w
        where w.organization.id = :organizationId
        group by w.wasteType
        order by w.wasteType asc
        """)
    List<Object[]> aggregateByType(
        @Param("organizationId")
        UUID organizationId
    );

    List<WasteRecord>
        findAllByOrganization_IdAndWasteTypeOrderByRecordedAtDesc(
            UUID organizationId,
            WasteType wasteType
        );
}