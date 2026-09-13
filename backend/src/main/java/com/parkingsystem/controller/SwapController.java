package com.parkingsystem.controller;

import com.parkingsystem.dto.swap.SwapDtos.SwapRequest;
import com.parkingsystem.dto.swap.SwapDtos.SwapResponse;
import com.parkingsystem.security.CustomUserDetails;
import com.parkingsystem.service.SwapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/swaps")
@RequiredArgsConstructor
public class SwapController {

    private final SwapService swapService;

    @GetMapping
    public List<SwapResponse> listMine(@AuthenticationPrincipal CustomUserDetails principal) {
        return swapService.listForUser(principal.getUser());
    }

    @PostMapping
    public ResponseEntity<SwapResponse> propose(@AuthenticationPrincipal CustomUserDetails principal,
                                                 @Valid @RequestBody SwapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(swapService.propose(principal.getUser(), request));
    }

    @PatchMapping("/{id}/accept")
    public SwapResponse accept(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        return swapService.accept(principal.getUser(), id);
    }

    @PatchMapping("/{id}/cancel")
    public void cancel(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        swapService.cancel(principal.getUser(), id);
    }
}
