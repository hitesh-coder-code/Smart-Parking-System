package com.parkingsystem.repository;

import com.parkingsystem.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    @EntityGraph(attributePaths = {"organization"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = {"organization"})
    List<User> findByOrganizationId(UUID orgId);

    @Override
    @EntityGraph(attributePaths = {"organization"})
    Optional<User> findById(UUID id);
}
