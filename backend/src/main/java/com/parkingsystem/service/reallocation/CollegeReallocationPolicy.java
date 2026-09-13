package com.parkingsystem.service.reallocation;

import com.parkingsystem.entity.Organization;
import com.parkingsystem.entity.ParkingSlot;
import com.parkingsystem.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/** In a college, a released slot goes to other faculty/staff — not students or admins. */
@Component
public class CollegeReallocationPolicy implements ReallocationPolicy {

    @Override
    public Organization.OrgType supports() {
        return Organization.OrgType.COLLEGE;
    }

    @Override
    public List<User> eligibleClaimants(List<User> orgUsers, ParkingSlot releasedSlot) {
        return orgUsers.stream()
                .filter(u -> u.getRole() == User.Role.FACULTY || u.getRole() == User.Role.STAFF)
                .filter(u -> releasedSlot.getOriginalOwner() == null
                        || !u.getId().equals(releasedSlot.getOriginalOwner().getId()))
                .toList();
    }
}
