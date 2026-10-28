package com.parkingsystem.controller;

import com.parkingsystem.dto.slot.SlotDtos.SlotRequest;
import com.parkingsystem.dto.slot.SlotDtos.SlotResponse;
import com.parkingsystem.security.CustomUserDetails;
import com.parkingsystem.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @GetMapping
    public List<SlotResponse> list(@AuthenticationPrincipal CustomUserDetails principal) {
        return slotService.listInMyOrg(principal.getUser());
    }

    @GetMapping("/available")
    public List<SlotResponse> listAvailable(@AuthenticationPrincipal CustomUserDetails principal) {
        return slotService.listAvailableInMyOrg(principal.getUser());
    }

    @PostMapping
    public ResponseEntity<SlotResponse> create(@AuthenticationPrincipal CustomUserDetails principal,
                                                @Valid @RequestBody SlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(slotService.create(principal.getUser(), request));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        slotService.delete(principal.getUser(), id);
    }

    /** Self-service: claim an unowned, available slot as your own permanent slot. */
    @PostMapping("/{id}/claim-permanent")
    public SlotResponse claimPermanent(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        return slotService.assignPermanent(principal.getUser(), id);
    }

    /** Self-service: give up your permanent slot, returning it to the unowned pool. */
    @PostMapping("/{id}/release")
    public SlotResponse release(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        return slotService.release(principal.getUser(), id);
    }
}
