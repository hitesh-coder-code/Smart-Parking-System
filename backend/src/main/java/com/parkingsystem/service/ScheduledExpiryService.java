package com.parkingsystem.service;

import com.parkingsystem.entity.ParkingSlot;
import com.parkingsystem.entity.SlotEvent;
import com.parkingsystem.entity.SwapAgreement;
import com.parkingsystem.repository.ParkingSlotRepository;
import com.parkingsystem.repository.SlotEventRepository;
import com.parkingsystem.repository.SwapAgreementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Runs periodically to auto-expire time-bound slot events and date-range swaps,
 * restoring each affected slot to its original owner once the window has passed.
 * This is the piece of the system that would, in production, run against the
 * managed database on a schedule (e.g. as a Render cron job or a Supabase/pg_cron task).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledExpiryService {

    private static final long FIVE_MINUTES_MS = 5 * 60 * 1000L;

    private final SlotEventRepository slotEventRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final SwapAgreementRepository swapAgreementRepository;
    private final SwapService swapService;

    @Scheduled(fixedRate = FIVE_MINUTES_MS)
    @Transactional
    public void expireLapsedEvents() {
        var lapsed = slotEventRepository.findByStatusAndEndAtBefore(SlotEvent.EventStatus.ACTIVE, Instant.now());
        for (SlotEvent event : lapsed) {
            ParkingSlot slot = event.getSlot();

            // Only auto-revert if nobody claimed it in the meantime; a CLAIMED slot
            // stays with its temporary holder until that holder's own event expires.
            if (slot.getStatus() != ParkingSlot.SlotStatus.CLAIMED) {
                slot.setAssignedTo(slot.getOriginalOwner());
                slot.setStatus(slot.getOriginalOwner() != null ? ParkingSlot.SlotStatus.RESERVED : ParkingSlot.SlotStatus.AVAILABLE);
                parkingSlotRepository.save(slot);
            }

            if (event.getTempVehicleSlot() != null) {
                ParkingSlot temp = event.getTempVehicleSlot();
                temp.setAssignedTo(null);
                temp.setStatus(ParkingSlot.SlotStatus.AVAILABLE);
                parkingSlotRepository.save(temp);
            }

            event.setStatus(SlotEvent.EventStatus.EXPIRED);
            slotEventRepository.save(event);
            log.info("Expired slot event {} for slot {}", event.getId(), slot.getSlotCode());
        }
    }

    @Scheduled(fixedRate = FIVE_MINUTES_MS)
    @Transactional
    public void expireLapsedSwaps() {
        LocalDate today = LocalDate.now();
        var active = swapAgreementRepository.findByStatus(SwapAgreement.SwapStatus.ACTIVE);
        for (SwapAgreement swap : active) {
            boolean lapsed = swap.getRecurrenceType() == SwapAgreement.RecurrenceType.DATE_RANGE
                    && swap.getEndDate() != null
                    && swap.getEndDate().isBefore(today);
            // ONE_TIME swaps are considered fulfilled the day after their single date
            boolean oneTimeDone = swap.getRecurrenceType() == SwapAgreement.RecurrenceType.ONE_TIME
                    && swap.getStartDate() != null
                    && swap.getStartDate().isBefore(today);

            if (lapsed || oneTimeDone) {
                swapService.revertSlots(swap);
                swap.setStatus(SwapAgreement.SwapStatus.EXPIRED);
                swapAgreementRepository.save(swap);
                log.info("Expired swap agreement {}", swap.getId());
            }
        }
    }
}
