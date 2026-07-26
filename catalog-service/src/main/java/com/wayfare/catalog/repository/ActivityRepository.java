package com.wayfare.catalog.repository;

import com.wayfare.catalog.domain.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Page<Activity> findByDestinationIdAndActiveTrue(UUID destinationId, Pageable pageable);

    /**
     * Prompt-grounding shortlist (design §7.3): active activities for a
     * destination, pre-filtered by a cost ceiling, best-rated first. Called by
     * the internal shortlist endpoint that Itinerary AI uses to build context.
     */
    // Native SQL: Hibernate 6.6's HQL `cast(:x as big_decimal)` mis-binds a null
    // BigDecimal parameter as bytea here (confirmed against real Postgres —
    // `cast(:q as string)` in DestinationRepository.search is unaffected, this
    // is specific to the numeric HQL cast path). A plain `::numeric` cast in a
    // native query binds correctly since JDBC sets the parameter type from the
    // BigDecimal argument directly, without Hibernate's HQL cast translation.
    @Query(value = """
            select * from activities
            where destination_id = :destinationId
              and active = true
              and (cast(:maxCost as numeric) is null or estimated_cost_usd <= cast(:maxCost as numeric))
            order by rating desc
            limit :limit
            """, nativeQuery = true)
    List<Activity> shortlist(@Param("destinationId") UUID destinationId,
                             @Param("maxCost") BigDecimal maxCost,
                             @Param("limit") int limit);
}
