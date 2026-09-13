package com.parkingsystem.service;

import com.parkingsystem.dto.user.UserResponse;
import com.parkingsystem.dto.user.UserUpdateRequest;
import com.parkingsystem.entity.User;
import com.parkingsystem.exception.ForbiddenException;
import com.parkingsystem.exception.ResourceNotFoundException;
import com.parkingsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /** The current user's own profile — safe to call before joining an organization. */
    @Transactional(readOnly = true)
    public UserResponse me(User requester) {
        return UserResponse.from(requester);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listInMyOrg(User requester) {
        OrgGuard.require(requester);
        return userRepository.findByOrganizationId(requester.getOrganization().getId())
                .stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getById(User requester, UUID id) {
        User target = findAndCheckSameOrg(requester, id);
        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse update(User requester, UUID id, UserUpdateRequest request) {
        requireAdmin(requester);
        User target = findAndCheckSameOrg(requester, id);

        if (request.getName() != null) target.setName(request.getName());
        if (request.getRole() != null) target.setRole(request.getRole());
        if (request.getDefaultVehicleType() != null) target.setDefaultVehicleType(request.getDefaultVehicleType());

        return UserResponse.from(userRepository.save(target));
    }

    @Transactional
    public void delete(User requester, UUID id) {
        requireAdmin(requester);
        User target = findAndCheckSameOrg(requester, id);
        userRepository.delete(target);
    }

    private User findAndCheckSameOrg(User requester, UUID targetId) {
        OrgGuard.require(requester);
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetId));
        if (!Objects.equals(target.getOrganization().getId(), requester.getOrganization().getId())) {
            throw new ForbiddenException("Cannot access users from a different organization");
        }
        return target;
    }

    private void requireAdmin(User requester) {
        if (requester.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("Only an admin can perform this action");
        }
    }
}
