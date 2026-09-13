package com.parkingsystem.controller;

import com.parkingsystem.dto.org.OrgDtos.CreateOrgRequest;
import com.parkingsystem.dto.org.OrgDtos.JoinOrgRequest;
import com.parkingsystem.dto.org.OrgDtos.PublicOrgResponse;
import com.parkingsystem.dto.user.UserResponse;
import com.parkingsystem.security.CustomUserDetails;
import com.parkingsystem.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orgs")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    /**
     * Deliberately public: shown on the post-login organization-selection screen,
     * and only ever returns id/name/type — nothing sensitive about the org.
     */
    @GetMapping("/public")
    public List<PublicOrgResponse> listPublic() {
        return organizationService.listPublic();
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@AuthenticationPrincipal CustomUserDetails principal,
                                                @Valid @RequestBody CreateOrgRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createOrganization(principal.getUser(), request));
    }

    @PostMapping("/{id}/join")
    public UserResponse join(@AuthenticationPrincipal CustomUserDetails principal,
                              @PathVariable UUID id,
                              @Valid @RequestBody JoinOrgRequest request) {
        return organizationService.joinOrganization(principal.getUser(), id, request);
    }
}
