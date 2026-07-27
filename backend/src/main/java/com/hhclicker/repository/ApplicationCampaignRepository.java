package com.hhclicker.repository;

import com.hhclicker.entity.ApplicationCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationCampaignRepository extends JpaRepository<ApplicationCampaign, UUID> {
    List<ApplicationCampaign> findAllByUserId(UUID userId);
    List<ApplicationCampaign> findAllByHhAccountId(UUID hhAccountId);
    List<ApplicationCampaign> findAllByStatusIn(List<com.hhclicker.enumeration.CampaignStatus> statuses);
    Optional<ApplicationCampaign> findByIdAndUserId(UUID id, UUID userId);
    void deleteAllByHhAccountId(UUID hhAccountId);
}
