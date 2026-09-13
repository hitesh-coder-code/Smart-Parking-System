package com.parkingsystem.service.reallocation;

import com.parkingsystem.entity.Organization;
import com.parkingsystem.entity.ParkingSlot;
import com.parkingsystem.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * In a shopping complex, a released staff slot goes into the customer pool —
 * deliberately NOT to other staff, since the goal is freeing up scarce customer
 * parking, not letting staff hoard each other's spots.
 */
@Component
public class ShoppingComplexReallocationPolicy implements ReallocationPolicy {

    @Override
    public Organization.OrgType supports() {
        return Organization.OrgType.SHOPPING_COMPLEX;
    }

    @Override
    public List<User> eligibleClaimants(List<User> orgUsers, ParkingSlot releasedSlot) {
        return orgUsers.stream()
                .filter(u -> u.getRole() == User.Role.CUSTOMER)
                .toList();
    }
}
