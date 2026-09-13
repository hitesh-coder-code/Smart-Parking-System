package com.parkingsystem.service.reallocation;

import com.parkingsystem.entity.Organization;
import com.parkingsystem.entity.ParkingSlot;
import com.parkingsystem.entity.User;

import java.util.List;

/**
 * Decides which users in an organization are allowed to claim a slot that
 * has just been released by an absence or vehicle-change event. Each
 * organization type has a different reallocation target group, which is
 * exactly the kind of policy variation this interface is meant to isolate
 * instead of scattering if/else chains across the service layer.
 */
public interface ReallocationPolicy {

    Organization.OrgType supports();

    /**
     * @param orgUsers all users belonging to the slot's organization
     * @param releasedSlot the slot that was just released
     * @return the subset of users eligible to submit a claim for this slot
     */
    List<User> eligibleClaimants(List<User> orgUsers, ParkingSlot releasedSlot);
}
