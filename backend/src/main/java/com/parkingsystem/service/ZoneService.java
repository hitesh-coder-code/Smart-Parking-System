package com.parkingsystem.service;

import com.parkingsystem.dto.zone.ZoneDtos.ZoneRequest;
import com.parkingsystem.dto.zone.ZoneDtos.ZoneResponse;
import com.parkingsystem.entity.Organization;
import com.parkingsystem.entity.User;
import com.parkingsystem.entity.Zone;
import com.parkingsystem.exception.ForbiddenException;
import com.parkingsystem.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public List<ZoneResponse> listInMyOrg(User requester) {
        Organization org = OrgGuard.require(requester);
        return zoneRepository.findByOrganizationIdOrderByNameAsc(org.getId())
                .stream().map(ZoneResponse::from).toList();
    }

    @Transactional
    public ZoneResponse create(User requester, ZoneRequest request) {
        Organization org = OrgGuard.require(requester);
        requireAdmin(requester);

        Zone zone = Zone.builder().organization(org).name(request.getName()).build();
        return ZoneResponse.from(zoneRepository.save(zone));
    }

    private void requireAdmin(User requester) {
        if (requester.getRole() != User.Role.ADMIN) {
            throw new ForbiddenException("Only an admin can perform this action");
        }
    }
}
