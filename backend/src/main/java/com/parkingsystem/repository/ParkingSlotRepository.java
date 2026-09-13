package com.parkingsystem.repository;

import com.parkingsystem.entity.ParkingSlot;
import com.parkingsystem.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, UUID> {
    @EntityGraph(attributePaths = {"zone", "assignedTo", "originalOwner"})
    List<ParkingSlot> findByOrganizationId(UUID orgId);

    @EntityGraph(attributePaths = {"zone", "assignedTo", "originalOwner"})
    List<ParkingSlot> findByOrganizationIdAndStatus(UUID orgId, ParkingSlot.SlotStatus status);

    @EntityGraph(attributePaths = {"zone", "assignedTo", "originalOwner"})
    List<ParkingSlot> findByOrganizationIdAndStatusAndVehicleType(
            UUID orgId, ParkingSlot.SlotStatus status, User.VehicleType vehicleType);

    @EntityGraph(attributePaths = {"zone", "assignedTo", "originalOwner"})
    List<ParkingSlot> findByOriginalOwnerId(UUID userId);
}
