package com.parkingsystem.service;

import com.parkingsystem.dto.swap.SwapDtos.SwapRequest;
import com.parkingsystem.dto.swap.SwapDtos.SwapResponse;
import com.parkingsystem.entity.*;
import com.parkingsystem.exception.BadRequestException;
import com.parkingsystem.exception.ForbiddenException;
import com.parkingsystem.exception.ResourceNotFoundException;
import com.parkingsystem.repository.ParkingSlotRepository;
import com.parkingsystem.repository.SwapAgreementRepository;
import com.parkingsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SwapService {

    private final SwapAgreementRepository swapAgreementRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final UserRepository userRepository;

    public List<SwapResponse> listForUser(User requester) {
        return swapAgreementRepository.findByUserAIdOrUserBId(requester.getId(), requester.getId())
                .stream().map(SwapResponse::from).toList();
    }

    @Transactional
    public SwapResponse propose(User requester, SwapRequest request) {
        OrgGuard.require(requester);
        User userB = userRepository.findById(request.getUserBId())
                .orElseThrow(() -> new ResourceNotFoundException("Other user not found"));
        if (!java.util.Objects.equals(
                userB.getOrganization() != null ? userB.getOrganization().getId() : null,
                requester.getOrganization().getId())) {
            throw new BadRequestException("Swaps are only allowed within the same organization");
        }

        ParkingSlot slotA = parkingSlotRepository.findById(request.getSlotAId())
                .orElseThrow(() -> new ResourceNotFoundException("Your slot was not found"));
        ParkingSlot slotB = parkingSlotRepository.findById(request.getSlotBId())
                .orElseThrow(() -> new ResourceNotFoundException("The other user's slot was not found"));

        if (slotA.getOriginalOwner() == null || !slotA.getOriginalOwner().getId().equals(requester.getId())) {
            throw new ForbiddenException("You can only offer a slot you own");
        }
        if (slotB.getOriginalOwner() == null || !slotB.getOriginalOwner().getId().equals(userB.getId())) {
            throw new BadRequestException("The target slot must belong to the other user");
        }

        validateRecurrenceFields(request);

        SwapAgreement swap = SwapAgreement.builder()
                .organization(requester.getOrganization())
                .userA(requester)
                .slotA(slotA)
                .userB(userB)
                .slotB(slotB)
                .recurrenceType(request.getRecurrenceType())
                .dayOfWeek(request.getDayOfWeek())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(SwapAgreement.SwapStatus.PENDING)
                .build();

        return SwapResponse.from(swapAgreementRepository.save(swap));
    }

    @Transactional
    public SwapResponse accept(User requester, UUID swapId) {
        SwapAgreement swap = swapAgreementRepository.findById(swapId)
                .orElseThrow(() -> new ResourceNotFoundException("Swap not found: " + swapId));

        if (!swap.getUserB().getId().equals(requester.getId())) {
            throw new ForbiddenException("Only the invited user can accept this swap");
        }
        if (swap.getStatus() != SwapAgreement.SwapStatus.PENDING) {
            throw new BadRequestException("This swap is no longer pending");
        }

        // exchange current holders; original ownership (original_owner) is left untouched
        // so the auto-expiry job knows where each slot ultimately belongs
        ParkingSlot slotA = swap.getSlotA();
        ParkingSlot slotB = swap.getSlotB();
        slotA.setAssignedTo(swap.getUserB());
        slotB.setAssignedTo(swap.getUserA());
        parkingSlotRepository.save(slotA);
        parkingSlotRepository.save(slotB);

        swap.setStatus(SwapAgreement.SwapStatus.ACTIVE);
        return SwapResponse.from(swapAgreementRepository.save(swap));
    }

    @Transactional
    public void cancel(User requester, UUID swapId) {
        SwapAgreement swap = swapAgreementRepository.findById(swapId)
                .orElseThrow(() -> new ResourceNotFoundException("Swap not found: " + swapId));

        boolean isParticipant = swap.getUserA().getId().equals(requester.getId())
                || swap.getUserB().getId().equals(requester.getId());
        if (!isParticipant && requester.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("Only a participant or an admin can cancel this swap");
        }

        if (swap.getStatus() == SwapAgreement.SwapStatus.ACTIVE) {
            revertSlots(swap);
        }
        swap.setStatus(SwapAgreement.SwapStatus.CANCELLED);
        swapAgreementRepository.save(swap);
    }

    /** Used by the scheduled expiry job when a DATE_RANGE swap's end_date has passed. */
    void revertSlots(SwapAgreement swap) {
        ParkingSlot slotA = swap.getSlotA();
        ParkingSlot slotB = swap.getSlotB();
        slotA.setAssignedTo(slotA.getOriginalOwner());
        slotB.setAssignedTo(slotB.getOriginalOwner());
        parkingSlotRepository.save(slotA);
        parkingSlotRepository.save(slotB);
    }

    private void validateRecurrenceFields(SwapRequest request) {
        switch (request.getRecurrenceType()) {
            case ONE_TIME -> {
                if (request.getStartDate() == null) {
                    throw new BadRequestException("startDate is required for a one-time swap");
                }
            }
            case WEEKLY -> {
                if (request.getDayOfWeek() == null) {
                    throw new BadRequestException("dayOfWeek is required for a weekly swap");
                }
            }
            case DATE_RANGE -> {
                if (request.getStartDate() == null || request.getEndDate() == null) {
                    throw new BadRequestException("startDate and endDate are required for a date-range swap");
                }
                if (!request.getEndDate().isAfter(request.getStartDate())) {
                    throw new BadRequestException("endDate must be after startDate");
                }
            }
        }
    }
}
