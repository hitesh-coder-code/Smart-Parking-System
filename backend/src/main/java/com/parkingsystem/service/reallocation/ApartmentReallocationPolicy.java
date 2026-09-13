package com.parkingsystem.service.reallocation;

import com.parkingsystem.entity.Organization;
import com.parkingsystem.entity.ParkingSlot;
import com.parkingsystem.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * In an apartment complex, a released resident slot goes to other residents
 * (modelling the "visitor pool" as simply "other residents", since this
 * project doesn't model an unauthenticated visitor role — see README).
 */
@Component
public class ApartmentReallocationPolicy implements ReallocationPolicy {

    @Override
    public Organization.OrgType supports() {
        return Organization.OrgType.APARTMENT;
    }

    @Override
    public List<User> eligibleClaimants(List<User> orgUsers, ParkingSlot releasedSlot) {
        return orgUsers.stream()
                .filter(u -> u.getRole() == User.Role.RESIDENT)
                .filter(u -> releasedSlot.getOriginalOwner() == null
                        || !u.getId().equals(releasedSlot.getOriginalOwner().getId()))
                .toList();
    }
}
