package com.parkingsystem.service.reallocation;

import com.parkingsystem.entity.Organization;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ReallocationPolicyFactory {

    private final Map<Organization.OrgType, ReallocationPolicy> policies;

    public ReallocationPolicyFactory(List<ReallocationPolicy> allPolicies) {
        this.policies = allPolicies.stream()
                .collect(Collectors.toMap(ReallocationPolicy::supports, Function.identity()));
    }

    public ReallocationPolicy getPolicy(Organization.OrgType orgType) {
        ReallocationPolicy policy = policies.get(orgType);
        if (policy == null) {
            throw new IllegalStateException("No reallocation policy registered for org type: " + orgType);
        }
        return policy;
    }
}
