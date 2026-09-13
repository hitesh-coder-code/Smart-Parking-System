package com.parkingsystem.service;

import com.parkingsystem.dto.slot.SlotDtos.SlotRequest;
import com.parkingsystem.dto.slot.SlotDtos.SlotResponse;
import com.parkingsystem.entity.Organization;
import com.parkingsystem.entity.ParkingSlot;
import com.parkingsystem.entity.User;
import com.parkingsystem.entity.Zone;
import com.parkingsystem.exception.BadRequestException;
import com.parkingsystem.exception.ForbiddenException;
import com.parkingsystem.exception.ResourceNotFoundException;
import com.parkingsystem.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final ParkingSlotRepository slotRepository;
    private final ZoneRepository zoneRepository;
    private final UserRepository userRepository;
    private final SlotEventRepository slotEventRepository;

    public List<SlotResponse> listInMyOrg(User requester) {
        Organization org = OrgGuard.require(requester);
        return slotRepository.findByOrganizationId(org.getId())
                .stream().map(SlotResponse::from).toList();
    }

    /** Slots currently free to interact with (released by an event, or never claimed at all). */
    public List<SlotResponse> listAvailableInMyOrg(User requester) {
        Organization org = OrgGuard.require(requester);
        return slotRepository.findByOrganizationIdAndStatus(org.getId(), ParkingSlot.SlotStatus.AVAILABLE)
                .stream().map(SlotResponse::from).toList();
    }

    public SlotResponse create(User requester, SlotRequest request) {
        Organization org = OrgGuard.require(requester);
        requireAdmin(requester);

        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found"));
        if (!zone.getOrganization().getId().equals(org.getId())) {
            throw new ForbiddenException("Cannot use a zone from a different organization");
        }

        User owner = null;
        if (request.getAssignedToUserId() != null) {
            owner = userRepository.findById(request.getAssignedToUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Owner user not found"));
            if (owner.getOrganization() == null || !owner.getOrganization().getId().equals(org.getId())) {
                throw new BadRequestException("The owner must belong to the same organization");
            }
        }

        ParkingSlot slot = ParkingSlot.builder()
                .organization(org)
                .zone(zone)
                .slotCode(request.getSlotCode())
                .vehicleType(request.getVehicleType())
                .assignedTo(owner)
                .originalOwner(owner)
                .status(owner != null ? ParkingSlot.SlotStatus.RESERVED : ParkingSlot.SlotStatus.AVAILABLE)
                .build();

        return SlotResponse.from(slotRepository.save(slot));
    }

    public void delete(User requester, UUID id) {
        requireAdmin(requester);
        ParkingSlot slot = findAndCheckSameOrg(requester, id);
        slotRepository.delete(slot);
    }

    /** Self-service: a user picks an unowned, available slot and makes it their permanent slot. */
    @Transactional
    public SlotResponse assignPermanent(User requester, UUID slotId) {
        OrgGuard.require(requester);
        ParkingSlot slot = findAndCheckSameOrg(requester, slotId);

        if (slot.getOriginalOwner() != null) {
            throw new BadRequestException("This slot already has a permanent owner");
        }
        if (slot.getStatus() != ParkingSlot.SlotStatus.AVAILABLE) {
            throw new BadRequestException("This slot is not currently available");
        }
        if (!slotRepository.findByOriginalOwnerId(requester.getId()).isEmpty()) {
            throw new BadRequestException("You already have a permanent slot — release it first");
        }

        slot.setOriginalOwner(requester);
        slot.setAssignedTo(requester);
        slot.setStatus(ParkingSlot.SlotStatus.RESERVED);
        return SlotResponse.from(slotRepository.save(slot));
    }

    /** Self-service: a user gives up their permanent slot, returning it to the unowned pool. */
    @Transactional
    public SlotResponse release(User requester, UUID slotId) {
        OrgGuard.require(requester);
        ParkingSlot slot = findAndCheckSameOrg(requester, slotId);

        boolean isOwner = slot.getOriginalOwner() != null && slot.getOriginalOwner().getId().equals(requester.getId());
        if (!isOwner && requester.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("Only the slot's owner or an admin can release it");
        }

        boolean hasActiveEvent = !slotEventRepository
                .findBySlotIdAndStatus(slot.getId(), com.parkingsystem.entity.SlotEvent.EventStatus.ACTIVE)
                .isEmpty();
        if (hasActiveEvent) {
            throw new BadRequestException("Cannot release this slot while it has an active absence/vehicle-change event — cancel that first");
        }

        slot.setOriginalOwner(null);
        slot.setAssignedTo(null);
        slot.setStatus(ParkingSlot.SlotStatus.AVAILABLE);
        return SlotResponse.from(slotRepository.save(slot));
    }

    ParkingSlot findAndCheckSameOrg(User requester, UUID slotId) {
        Organization org = OrgGuard.require(requester);
        ParkingSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + slotId));
        if (!Objects.equals(slot.getOrganization().getId(), org.getId())) {
            throw new ForbiddenException("Cannot access a slot from a different organization");
        }
        return slot;
    }

    private void requireAdmin(User requester) {
        if (requester.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("Only an admin can perform this action");
        }
    }
}
