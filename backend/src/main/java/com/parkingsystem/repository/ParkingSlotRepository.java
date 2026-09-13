package com.parkingsystem.repository;

import com.parkingsystem.entity.ParkingSlot;
import com.parkingsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, UUID> {
    List<ParkingSlot> findByOrganizationId(UUID orgId);

    List<ParkingSlot> findByOrganizationIdAndStatus(UUID orgId, ParkingSlot.SlotStatus status);

    List<ParkingSlot> findByOrganizationIdAndStatusAndVehicleType(
            UUID orgId, ParkingSlot.SlotStatus status, User.VehicleType vehicleType);

    List<ParkingSlot> findByOriginalOwnerId(UUID userId);
}
