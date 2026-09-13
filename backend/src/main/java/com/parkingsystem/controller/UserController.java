package com.parkingsystem.controller;

import com.parkingsystem.dto.user.UserResponse;
import com.parkingsystem.dto.user.UserUpdateRequest;
import com.parkingsystem.security.CustomUserDetails;
import com.parkingsystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** The logged-in user's own profile — works even before joining an organization. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CustomUserDetails principal) {
        return userService.me(principal.getUser());
    }

    @GetMapping
    public List<UserResponse> list(@AuthenticationPrincipal CustomUserDetails principal) {
        return userService.listInMyOrg(principal.getUser());
    }

    @GetMapping("/{id}")
    public UserResponse get(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        return userService.getById(principal.getUser(), id);
    }

    @PutMapping("/{id}")
    public UserResponse update(@AuthenticationPrincipal CustomUserDetails principal,
                                @PathVariable UUID id, @RequestBody UserUpdateRequest request) {
        return userService.update(principal.getUser(), id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        userService.delete(principal.getUser(), id);
    }
}
