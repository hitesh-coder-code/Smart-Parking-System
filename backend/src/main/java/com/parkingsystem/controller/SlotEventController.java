package com.parkingsystem.controller;

import com.parkingsystem.dto.event.SlotEventDtos.SlotEventRequest;
import com.parkingsystem.dto.event.SlotEventDtos.SlotEventResponse;
import com.parkingsystem.security.CustomUserDetails;
import com.parkingsystem.service.SlotEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/slot-events")
@RequiredArgsConstructor
public class SlotEventController {

    private final SlotEventService slotEventService;

    @GetMapping
    public List<SlotEventResponse> listForSlot(@AuthenticationPrincipal CustomUserDetails principal,
                                                @RequestParam UUID slotId) {
        return slotEventService.listForSlot(principal.getUser(), slotId);
    }

    /** event_type in the body must be ABSENCE or VEHICLE_CHANGE; each is routed to its own logic below. */
    @PostMapping("/absence")
    public ResponseEntity<SlotEventResponse> reportAbsence(@AuthenticationPrincipal CustomUserDetails principal,
                                                             @Valid @RequestBody SlotEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(slotEventService.reportAbsence(principal.getUser(), request));
    }

    @PostMapping("/vehicle-change")
    public ResponseEntity<SlotEventResponse> reportVehicleChange(@AuthenticationPrincipal CustomUserDetails principal,
                                                                   @Valid @RequestBody SlotEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(slotEventService.reportVehicleChange(principal.getUser(), request));
    }

    @PatchMapping("/{id}/cancel")
    public void cancel(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        slotEventService.cancel(principal.getUser(), id);
    }
}
