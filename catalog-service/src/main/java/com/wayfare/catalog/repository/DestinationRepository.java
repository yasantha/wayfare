package com.wayfare.catalog.repository;

import com.wayfare.catalog.domain.Destination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DestinationRepository extends JpaRepository<Destination, UUID> {

    // Explicit `cast(:x as string)` avoids a Postgres/Hibernate parameter-type
    // ambiguity: an untyped null bound both in an `is null` check and inside
    // lower(...) in the same query resolves to bytea and lower() then fails.
    @Query("""
            select d from Destination d
            where (cast(:q as string) is null or lower(d.name) like lower(concat('%', cast(:q as string), '%'))
                              or lower(d.region) like lower(concat('%', cast(:q as string), '%')))
              and (cast(:countryCode as string) is null or d.countryCode = cast(:countryCode as string))
            """)
    Page<Destination> search(@Param("q") String query, @Param("countryCode") String countryCode, Pageable pageable);
}
