package com.parkingsystem.repository;

import com.parkingsystem.entity.SwapAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SwapAgreementRepository extends JpaRepository<SwapAgreement, UUID> {
    List<SwapAgreement> findByUserAIdOrUserBId(UUID userAId, UUID userBId);

    List<SwapAgreement> findByStatus(SwapAgreement.SwapStatus status);
}
