package com.parkingsystem.repository;

import com.parkingsystem.entity.SlotEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SlotEventRepository extends JpaRepository<SlotEvent, UUID> {
    List<SlotEvent> findBySlotId(UUID slotId);

    List<SlotEvent> findBySlotIdAndStatus(UUID slotId, SlotEvent.EventStatus status);

    List<SlotEvent> findByStatusAndEndAtBefore(SlotEvent.EventStatus status, Instant instant);
}
