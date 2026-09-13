package com.parkingsystem.controller;

import com.parkingsystem.dto.zone.ZoneDtos.ZoneRequest;
import com.parkingsystem.dto.zone.ZoneDtos.ZoneResponse;
import com.parkingsystem.security.CustomUserDetails;
import com.parkingsystem.service.ZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @GetMapping
    public List<ZoneResponse> list(@AuthenticationPrincipal CustomUserDetails principal) {
        return zoneService.listInMyOrg(principal.getUser());
    }

    @PostMapping
    public ResponseEntity<ZoneResponse> create(@AuthenticationPrincipal CustomUserDetails principal,
                                                @Valid @RequestBody ZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneService.create(principal.getUser(), request));
    }
}
