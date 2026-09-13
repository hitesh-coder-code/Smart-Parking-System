package com.parkingsystem.service;

import com.parkingsystem.entity.Organization;
import com.parkingsystem.entity.User;
import com.parkingsystem.exception.BadRequestException;

/**
 * Centralizes the "does this user belong to an organization yet" check.
 * Every service method that assumes org membership calls this first, both
 * to fail with a clear message instead of a NullPointerException, and to
 * make the requirement visible at a glance in each method.
 */
public final class OrgGuard {

    private OrgGuard() {}

    public static Organization require(User user) {
        if (user.getOrganization() == null) {
            throw new BadRequestException("You must join or create an organization first");
        }
        return user.getOrganization();
    }
}
