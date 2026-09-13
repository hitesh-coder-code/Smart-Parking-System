package com.parkingsystem.controller;

import com.parkingsystem.dto.claim.SlotClaimDtos.ClaimRequest;
import com.parkingsystem.dto.claim.SlotClaimDtos.ClaimResponse;
import com.parkingsystem.security.CustomUserDetails;
import com.parkingsystem.service.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    @GetMapping
    public List<ClaimResponse> listForEvent(@AuthenticationPrincipal CustomUserDetails principal,
                                             @RequestParam UUID eventId) {
        return claimService.listForEvent(principal.getUser(), eventId);
    }

    @PostMapping
    public ResponseEntity<ClaimResponse> create(@AuthenticationPrincipal CustomUserDetails principal,
                                                 @Valid @RequestBody ClaimRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(claimService.create(principal.getUser(), request));
    }

    @PatchMapping("/{id}/approve")
    public ClaimResponse approve(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        return claimService.approve(principal.getUser(), id);
    }

    @PatchMapping("/{id}/reject")
    public ClaimResponse reject(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        return claimService.reject(principal.getUser(), id);
    }
}
