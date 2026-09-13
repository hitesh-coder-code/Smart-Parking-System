package com.parkingsystem.repository;

import com.parkingsystem.entity.Zone;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    @EntityGraph(attributePaths = {"organization"})
    List<Zone> findByOrganizationId(UUID orgId);

    @EntityGraph(attributePaths = {"organization"})
    List<Zone> findByOrganizationIdOrderByNameAsc(UUID orgId);
}
