package com.parkingsystem.service;

import com.parkingsystem.dto.event.SlotEventDtos.SlotEventRequest;
import com.parkingsystem.dto.event.SlotEventDtos.SlotEventResponse;
import com.parkingsystem.entity.ParkingSlot;
import com.parkingsystem.entity.SlotEvent;
import com.parkingsystem.entity.User;
import com.parkingsystem.exception.BadRequestException;
import com.parkingsystem.exception.ForbiddenException;
import com.parkingsystem.exception.ResourceNotFoundException;
import com.parkingsystem.repository.ParkingSlotRepository;
import com.parkingsystem.repository.SlotEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SlotEventService {

    private final SlotEventRepository slotEventRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final SlotService slotService;

    public List<SlotEventResponse> listForSlot(User requester, UUID slotId) {
        slotService.findAndCheckSameOrg(requester, slotId); // also validates org access
        return slotEventRepository.findBySlotId(slotId).stream().map(SlotEventResponse::from).toList();
    }

    @Transactional
    public SlotEventResponse reportAbsence(User requester, SlotEventRequest request) {
        ParkingSlot slot = slotService.findAndCheckSameOrg(requester, request.getSlotId());
        requireOwnerOrAdmin(requester, slot);
        requireNoActiveEvent(slot);

        SlotEvent event = SlotEvent.builder()
                .slot(slot)
                .triggeredBy(requester)
                .eventType(SlotEvent.EventType.ABSENCE)
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .recurrenceRule(request.getRecurrenceRule())
                .status(SlotEvent.EventStatus.ACTIVE)
                .build();
        event = slotEventRepository.save(event);

        // The slot enters the claimable pool immediately; assignedTo is left as-is
        // until a claim is approved, so we still know who to notify/revert to.
        slot.setStatus(ParkingSlot.SlotStatus.AVAILABLE);
        parkingSlotRepository.save(slot);

        return SlotEventResponse.from(event);
    }

    @Transactional
    public SlotEventResponse reportVehicleChange(User requester, SlotEventRequest request) {
        ParkingSlot slot = slotService.findAndCheckSameOrg(requester, request.getSlotId());
        requireOwnerOrAdmin(requester, slot);
        requireNoActiveEvent(slot);

        if (slot.getVehicleType() != User.VehicleType.FOUR_WHEELER) {
            throw new BadRequestException("Vehicle-change events only apply to four-wheeler slots");
        }

        // Look for a free two-wheeler pool slot in the same zone first, then anywhere in the org.
        ParkingSlot tempSlot = parkingSlotRepository
                .findByOrganizationIdAndStatusAndVehicleType(
                        slot.getOrganization().getId(), ParkingSlot.SlotStatus.AVAILABLE, User.VehicleType.TWO_WHEELER)
                .stream()
                .filter(s -> s.getOriginalOwner() == null) // only pool slots, not someone else's reserved 2W spot
                .sorted((a, b) -> Boolean.compare(
                        !b.getZone().getId().equals(slot.getZone().getId()),
                        !a.getZone().getId().equals(slot.getZone().getId())))
                .findFirst()
                .orElse(null);

        SlotEvent event = SlotEvent.builder()
                .slot(slot)
                .triggeredBy(requester)
                .eventType(SlotEvent.EventType.VEHICLE_CHANGE)
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .recurrenceRule(request.getRecurrenceRule())
                .status(SlotEvent.EventStatus.ACTIVE)
                .tempVehicleSlot(tempSlot)
                .build();
        event = slotEventRepository.save(event);

        // release the 4W slot into the pool
        slot.setStatus(ParkingSlot.SlotStatus.AVAILABLE);
        parkingSlotRepository.save(slot);

        // if a 2W slot was found, hand it to the requester for the duration of the event
        if (tempSlot != null) {
            tempSlot.setAssignedTo(requester);
            tempSlot.setStatus(ParkingSlot.SlotStatus.CLAIMED);
            parkingSlotRepository.save(tempSlot);
        }

        return SlotEventResponse.from(event);
    }

    @Transactional
    public void cancel(User requester, UUID eventId) {
        SlotEvent event = slotEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (!event.getSlot().getOrganization().getId().equals(requester.getOrganization().getId())) {
            throw new ForbiddenException("Cannot access an event from a different organization");
        }
        if (!event.getTriggeredBy().getId().equals(requester.getId()) && requester.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("Only the reporting user or an admin can cancel this event");
        }
        if (event.getStatus() != SlotEvent.EventStatus.ACTIVE) {
            throw new BadRequestException("Only an active event can be cancelled");
        }

        ParkingSlot slot = event.getSlot();
        if (slot.getStatus() == ParkingSlot.SlotStatus.CLAIMED) {
            throw new BadRequestException("Cannot cancel: this slot has already been claimed by another user");
        }

        // revert the original slot back to its owner
        slot.setAssignedTo(slot.getOriginalOwner());
        slot.setStatus(slot.getOriginalOwner() != null ? ParkingSlot.SlotStatus.RESERVED : ParkingSlot.SlotStatus.AVAILABLE);
        parkingSlotRepository.save(slot);

        // release any temporary two-wheeler slot back into the pool
        if (event.getTempVehicleSlot() != null) {
            ParkingSlot temp = event.getTempVehicleSlot();
            temp.setAssignedTo(null);
            temp.setStatus(ParkingSlot.SlotStatus.AVAILABLE);
            parkingSlotRepository.save(temp);
        }

        event.setStatus(SlotEvent.EventStatus.CANCELLED);
        slotEventRepository.save(event);
    }

    private void requireOwnerOrAdmin(User requester, ParkingSlot slot) {
        boolean isOwner = slot.getOriginalOwner() != null && slot.getOriginalOwner().getId().equals(requester.getId());
        if (!isOwner && requester.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("Only the slot's owner or an admin can report this event");
        }
    }

    private void requireNoActiveEvent(ParkingSlot slot) {
        boolean hasActive = slotEventRepository.findBySlotIdAndStatus(slot.getId(), SlotEvent.EventStatus.ACTIVE)
                .stream().findAny().isPresent();
        if (hasActive) {
            throw new BadRequestException("This slot already has an active event");
        }
    }

}
