package com.parkingsystem.service;

import com.parkingsystem.dto.claim.SlotClaimDtos.ClaimRequest;
import com.parkingsystem.dto.claim.SlotClaimDtos.ClaimResponse;
import com.parkingsystem.entity.*;
import com.parkingsystem.exception.BadRequestException;
import com.parkingsystem.exception.ForbiddenException;
import com.parkingsystem.exception.ResourceNotFoundException;
import com.parkingsystem.repository.*;
import com.parkingsystem.service.reallocation.ReallocationPolicy;
import com.parkingsystem.service.reallocation.ReallocationPolicyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimService {

    private final SlotClaimRepository slotClaimRepository;
    private final SlotEventRepository slotEventRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final ReallocationPolicyFactory policyFactory;
    private final ProximityService proximityService;

    public List<ClaimResponse> listForEvent(User requester, UUID eventId) {
        SlotEvent event = getEventInOrg(requester, eventId);
        return slotClaimRepository.findByEventId(event.getId()).stream().map(ClaimResponse::from).toList();
    }

    @Transactional
    public ClaimResponse create(User requester, ClaimRequest request) {
        SlotEvent event = getEventInOrg(requester, request.getEventId());
        ParkingSlot slot = event.getSlot();

        if (event.getStatus() != SlotEvent.EventStatus.ACTIVE) {
            throw new BadRequestException("This event is no longer active");
        }
        if (slot.getStatus() != ParkingSlot.SlotStatus.AVAILABLE) {
            throw new BadRequestException("This slot has already been claimed");
        }

        Organization org = slot.getOrganization();
        List<User> orgUsers = userRepository.findByOrganizationId(org.getId());
        ReallocationPolicy policy = policyFactory.getPolicy(org.getType());
        boolean eligible = policy.eligibleClaimants(orgUsers, slot).stream()
                .anyMatch(u -> u.getId().equals(requester.getId()));
        if (!eligible) {
            throw new ForbiddenException("You are not eligible to claim this slot in this organization");
        }

        Zone destinationZone = null;
        if (request.getDestinationZoneId() != null) {
            destinationZone = zoneRepository.findById(request.getDestinationZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination zone not found"));
        }
        Integer rank = proximityService.rank(request.getDestinationZoneId(), slot.getZone().getId());

        SlotClaim claim = SlotClaim.builder()
                .event(event)
                .claimedBy(requester)
                .destinationZone(destinationZone)
                .proximityRank(rank)
                .status(SlotClaim.ClaimStatus.PENDING)
                .build();
        claim = slotClaimRepository.save(claim);

        // First eligible claim on a still-available slot wins immediately (first-come-first-served).
        // Proximity rank is stored purely as informational data — it never decides who gets the slot.
        return approveInternal(claim, slot);
    }

    @Transactional
    public ClaimResponse approve(User requester, UUID claimId) {
        SlotClaim claim = slotClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found: " + claimId));
        ParkingSlot slot = claim.getEvent().getSlot();
        checkOrgAccess(requester, slot);
        requireAdmin(requester);

        if (slot.getStatus() != ParkingSlot.SlotStatus.AVAILABLE) {
            throw new BadRequestException("Slot is no longer available to approve a claim for");
        }
        return approveInternal(claim, slot);
    }

    @Transactional
    public ClaimResponse reject(User requester, UUID claimId) {
        SlotClaim claim = slotClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found: " + claimId));
        checkOrgAccess(requester, claim.getEvent().getSlot());
        requireAdmin(requester);

        claim.setStatus(SlotClaim.ClaimStatus.REJECTED);
        return ClaimResponse.from(slotClaimRepository.save(claim));
    }

    private ClaimResponse approveInternal(SlotClaim claim, ParkingSlot slot) {
        claim.setStatus(SlotClaim.ClaimStatus.APPROVED);
        slotClaimRepository.save(claim);

        slot.setAssignedTo(claim.getClaimedBy());
        slot.setStatus(ParkingSlot.SlotStatus.CLAIMED);
        parkingSlotRepository.save(slot);

        // any other still-pending claims on the same event lose out
        slotClaimRepository.findByEventIdAndStatus(claim.getEvent().getId(), SlotClaim.ClaimStatus.PENDING)
                .forEach(other -> {
                    if (!other.getId().equals(claim.getId())) {
                        other.setStatus(SlotClaim.ClaimStatus.REJECTED);
                        slotClaimRepository.save(other);
                    }
                });

        return ClaimResponse.from(claim);
    }

    private SlotEvent getEventInOrg(User requester, UUID eventId) {
        SlotEvent event = slotEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        checkOrgAccess(requester, event.getSlot());
        return event;
    }

    private void checkOrgAccess(User requester, ParkingSlot slot) {
        OrgGuard.require(requester);
        if (!java.util.Objects.equals(slot.getOrganization().getId(), requester.getOrganization().getId())) {
            throw new ForbiddenException("Cannot access data from a different organization");
        }
    }

    private void requireAdmin(User requester) {
        if (requester.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("Only an admin can perform this action");
        }
    }

}
