package com.parkingsystem.service;

import com.parkingsystem.dto.org.OrgDtos.CreateOrgRequest;
import com.parkingsystem.dto.org.OrgDtos.JoinOrgRequest;
import com.parkingsystem.dto.org.OrgDtos.PublicOrgResponse;
import com.parkingsystem.dto.user.UserResponse;
import com.parkingsystem.entity.Organization;
import com.parkingsystem.entity.User;
import com.parkingsystem.exception.BadRequestException;
import com.parkingsystem.exception.ResourceNotFoundException;
import com.parkingsystem.repository.OrganizationRepository;
import com.parkingsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    /** Which roles a *joining* user may pick per organization type. ADMIN is never joinable — it's only granted to the creator. */
    private static final Map<Organization.OrgType, List<User.Role>> JOINABLE_ROLES = Map.of(
            Organization.OrgType.COLLEGE, List.of(User.Role.FACULTY, User.Role.STAFF),
            Organization.OrgType.SHOPPING_COMPLEX, List.of(User.Role.STAFF, User.Role.CUSTOMER),
            Organization.OrgType.APARTMENT, List.of(User.Role.RESIDENT)
    );

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public List<PublicOrgResponse> listPublic() {
        return organizationRepository.findAll().stream().map(PublicOrgResponse::from).toList();
    }

    public Map<Organization.OrgType, List<User.Role>> joinableRoles() {
        return JOINABLE_ROLES;
    }

    @Transactional
    public UserResponse createOrganization(User requester, CreateOrgRequest request) {
        if (requester.getOrganization() != null) {
            throw new BadRequestException("You already belong to an organization");
        }

        Organization org = Organization.builder()
                .name(request.getName())
                .type(request.getType())
                .build();
        org = organizationRepository.save(org);

        requester.setOrganization(org);
        requester.setRole(User.Role.ADMIN);
        requester.setDefaultVehicleType(
                request.getDefaultVehicleType() != null ? request.getDefaultVehicleType() : User.VehicleType.FOUR_WHEELER);
        userRepository.save(requester);

        return UserResponse.from(requester);
    }

    @Transactional
    public UserResponse joinOrganization(User requester, UUID orgId, JoinOrgRequest request) {
        if (requester.getOrganization() != null) {
            throw new BadRequestException("You already belong to an organization");
        }

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + orgId));

        List<User.Role> allowed = JOINABLE_ROLES.get(org.getType());
        if (allowed == null || !allowed.contains(request.getRole())) {
            throw new BadRequestException(
                    "Role " + request.getRole() + " is not valid for a " + org.getType() + " organization. Allowed roles: " + allowed);
        }

        requester.setOrganization(org);
        requester.setRole(request.getRole());
        requester.setDefaultVehicleType(
                request.getDefaultVehicleType() != null ? request.getDefaultVehicleType() : User.VehicleType.FOUR_WHEELER);
        userRepository.save(requester);

        return UserResponse.from(requester);
    }
}
